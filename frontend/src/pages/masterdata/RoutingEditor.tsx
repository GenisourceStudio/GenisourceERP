import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'

import Lookup, { type LookupOption } from '../../components/Lookup'
import { ApiError } from '../../shared/api/client'
import {
  copyRouting,
  createRouting,
  deleteRouting,
  itemOptions,
  obsoleteRouting,
  releaseRouting,
  updateRouting,
  type RoutingDetail,
  type RoutingRequest,
  type WorkCenterView,
} from '../../shared/api/masterdata'

interface OperationDraft {
  key: string
  seq: number | null
  workCenterId: number
  name: string
  setupMinutes: number
  runMinutesPerUnit: number
  remark: string | null
}

interface RoutingEditorProps {
  initial: RoutingDetail | null
  workCenters: WorkCenterView[]
  onSaved: (routing: RoutingDetail) => void
  onClose: () => void
}

function newOperation(index: number): OperationDraft {
  return {
    key: `op-${index}-${Math.random().toString(36).slice(2, 8)}`,
    seq: null,
    workCenterId: 0,
    name: '',
    setupMinutes: 0,
    runMinutesPerUnit: 0,
    remark: null,
  }
}

export default function RoutingEditor({
  initial,
  workCenters,
  onSaved,
  onClose,
}: RoutingEditorProps) {
  const { t } = useTranslation()

  const [item, setItem] = useState<LookupOption | null>(
    initial && initial.itemId
      ? { id: initial.itemId, code: initial.itemCode ?? '', name: initial.itemName ?? '' }
      : null,
  )
  const [version, setVersion] = useState(initial?.version ?? 'V1')
  const [remark, setRemark] = useState(initial?.remark ?? '')
  const [operations, setOperations] = useState<OperationDraft[]>(
    initial
      ? initial.operations.map((operation, index) => ({
          key: `op-${index}`,
          seq: operation.seq,
          workCenterId: operation.workCenterId,
          name: operation.name,
          setupMinutes: operation.setupMinutes,
          runMinutesPerUnit: operation.runMinutesPerUnit,
          remark: operation.remark,
        }))
      : [newOperation(0)],
  )
  const [copyVersion, setCopyVersion] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const locked = initial !== null && initial.status !== 'DRAFT'

  const searchItems = useCallback(async (query: string): Promise<LookupOption[]> => {
    const options = await itemOptions(query, 20)
    return options.map((option) => ({ id: option.id, code: option.code, name: option.name }))
  }, [])

  const patchOperation = (key: string, changes: Partial<OperationDraft>) => {
    setOperations((current) =>
      current.map((operation) => (operation.key === key ? { ...operation, ...changes } : operation)),
    )
  }

  const payload: RoutingRequest = {
    itemId: item?.id ?? 0,
    version,
    remark: remark || null,
    operations: operations
      .filter((operation) => operation.workCenterId > 0 && operation.name.trim().length > 0)
      .map((operation) => ({
        seq: operation.seq,
        workCenterId: operation.workCenterId,
        name: operation.name.trim(),
        setupMinutes: operation.setupMinutes,
        runMinutesPerUnit: operation.runMinutesPerUnit,
        remark: operation.remark,
      })),
  }

  const run = async (action: () => Promise<RoutingDetail>) => {
    setBusy(true)
    setError(null)
    try {
      onSaved(await action())
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!initial) {
      return
    }
    setBusy(true)
    try {
      await deleteRouting(initial.id)
      onClose()
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setBusy(false)
    }
  }

  const activeWorkCenters = workCenters.filter(
    (center) => center.status === 'ACTIVE' || operations.some((op) => op.workCenterId === center.id),
  )

  return (
    <div>
      {error ? <div className="form-error">{error}</div> : null}
      {locked ? (
        <div
          className="form-error"
          style={{
            borderColor: 'rgba(168,106,18,0.35)',
            borderLeftColor: 'var(--amber)',
            background: 'rgba(168,106,18,0.07)',
            color: 'var(--amber)',
          }}
        >
          {t('bom.hint.locked', { status: t(`status.revision.${initial?.status}`) })}
        </div>
      ) : null}

      <div className="form-grid form-grid--3">
        <div className="field span-2">
          <label htmlFor="routing-item">{t('routing.field.item')}</label>
          <Lookup
            id="routing-item"
            value={item}
            onChange={setItem}
            search={searchItems}
            disabled={locked || initial !== null}
            placeholder={t('bom.placeholder.item')}
          />
        </div>
        <div className="field">
          <label htmlFor="routing-version">{t('routing.field.version')}</label>
          <input
            id="routing-version"
            className="input"
            value={version}
            maxLength={16}
            disabled={locked}
            onChange={(event) => setVersion(event.target.value.toUpperCase())}
          />
        </div>
      </div>

      <div className="form-section">{t('routing.section.operations')}</div>
      {operations.map((operation, index) => (
        <div key={operation.key} className="bom-line">
          <div
            className="bom-line__head"
            style={{ gridTemplateColumns: '38px minmax(0,1.4fr) minmax(0,1.6fr) 82px 92px 30px' }}
          >
            <div className="bom-line__no">#{operation.seq ?? (index + 1) * 10}</div>
            <div className="field">
              <label>{t('routing.field.workCenter')}</label>
              <select
                className="select"
                value={operation.workCenterId}
                disabled={locked}
                onChange={(event) =>
                  patchOperation(operation.key, { workCenterId: Number(event.target.value) })
                }
              >
                <option value={0}>{t('routing.placeholder.workCenter')}</option>
                {activeWorkCenters.map((center) => (
                  <option key={center.id} value={center.id}>
                    {center.code} · {center.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>{t('routing.field.name')}</label>
              <input
                className="input"
                value={operation.name}
                maxLength={128}
                disabled={locked}
                placeholder={t('routing.placeholder.name')}
                onChange={(event) => patchOperation(operation.key, { name: event.target.value })}
              />
            </div>
            <div className="field">
              <label>{t('routing.field.setupMinutes')}</label>
              <input
                className="input input--num"
                type="number"
                min={0}
                step="0.1"
                value={operation.setupMinutes}
                disabled={locked}
                onChange={(event) =>
                  patchOperation(operation.key, { setupMinutes: Number(event.target.value) || 0 })
                }
              />
            </div>
            <div className="field">
              <label>{t('routing.field.runMinutes')}</label>
              <input
                className="input input--num"
                type="number"
                min={0}
                step="0.001"
                value={operation.runMinutesPerUnit}
                disabled={locked}
                onChange={(event) =>
                  patchOperation(operation.key, {
                    runMinutesPerUnit: Number(event.target.value) || 0,
                  })
                }
              />
            </div>
            <button
              type="button"
              className="line-table__remove"
              disabled={locked}
              title={t('common.remove')}
              onClick={() =>
                setOperations((current) => current.filter((entry) => entry.key !== operation.key))
              }
            >
              ✕
            </button>
          </div>
          <div className="bom-line__sub">
            <label>{t('common.remark')}</label>
            <input
              className="input"
              value={operation.remark ?? ''}
              maxLength={255}
              disabled={locked}
              onChange={(event) =>
                patchOperation(operation.key, { remark: event.target.value || null })
              }
            />
          </div>
        </div>
      ))}

      {locked ? null : (
        <div className="line-actions">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setOperations((current) => [...current, newOperation(current.length)])}
          >
            + {t('routing.action.addOperation')}
          </button>
          <p className="hint" style={{ margin: 0, alignSelf: 'center' }}>
            {t('routing.hint.sequence')}
          </p>
        </div>
      )}

      <div className="form-section">{t('common.remark')}</div>
      <div className="field">
        <textarea
          className="input"
          value={remark ?? ''}
          maxLength={500}
          disabled={locked}
          onChange={(event) => setRemark(event.target.value)}
        />
      </div>

      <div className="line-actions" style={{ justifyContent: 'flex-end', flexWrap: 'wrap' }}>
        <button type="button" className="btn btn--ghost btn--sm" onClick={onClose} disabled={busy}>
          {t('common.close')}
        </button>
        {locked && initial ? (
          <>
            <input
              className="input"
              style={{ width: 120, height: 28 }}
              placeholder={t('bom.placeholder.newVersion')}
              value={copyVersion}
              onChange={(event) => setCopyVersion(event.target.value.toUpperCase())}
            />
            <button
              type="button"
              className="btn btn--sm"
              disabled={busy || copyVersion.trim().length === 0}
              onClick={() => run(() => copyRouting(initial.id, copyVersion))}
            >
              {t('bom.action.copy')}
            </button>
            {initial.status === 'RELEASED' ? (
              <button
                type="button"
                className="btn btn--danger btn--sm"
                disabled={busy}
                onClick={() => run(() => obsoleteRouting(initial.id))}
              >
                {t('bom.action.obsolete')}
              </button>
            ) : null}
          </>
        ) : null}
        {!locked ? (
          <>
            {initial ? (
              <button
                type="button"
                className="btn btn--danger btn--sm"
                disabled={busy}
                onClick={remove}
              >
                {t('common.delete')}
              </button>
            ) : null}
            <button
              type="button"
              className="btn btn--sm"
              disabled={busy}
              onClick={() => run(() => (initial ? updateRouting(initial.id, payload) : createRouting(payload)))}
            >
              {t('common.save')}
            </button>
            <button
              type="button"
              className="btn btn--primary btn--sm"
              disabled={busy}
              onClick={async () => {
                setBusy(true)
                setError(null)
                try {
                  const saved = initial
                    ? await updateRouting(initial.id, payload)
                    : await createRouting(payload)
                  onSaved(await releaseRouting(saved.id))
                } catch (exception) {
                  setError(
                    exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'),
                  )
                } finally {
                  setBusy(false)
                }
              }}
            >
              {t('bom.action.saveAndRelease')}
            </button>
          </>
        ) : null}
      </div>
    </div>
  )
}

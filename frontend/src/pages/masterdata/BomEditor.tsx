import { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import Lookup, { type LookupOption } from '../../components/Lookup'
import { ApiError } from '../../shared/api/client'
import {
  copyBom,
  createBom,
  deleteBom,
  itemOptions,
  obsoleteBom,
  releaseBom,
  updateBom,
  type BomDetail,
  type BomRequest,
} from '../../shared/api/masterdata'

/** Editable line: the request shape plus the display label the picker needs. */
interface LineDraft {
  key: string
  lineNo: number | null
  componentItemId: number
  component: LookupOption | null
  qty: number
  lossRate: number
  phantom: boolean
  remark: string | null
  substitutes: (LookupOption & { priority: number })[]
}

interface BomEditorProps {
  initial: BomDetail | null
  /** Prefills the parent item when creating from an item context. */
  onSaved: (bom: BomDetail) => void
  onClose: () => void
}

function newLine(index: number): LineDraft {
  return {
    key: `line-${index}-${Math.random().toString(36).slice(2, 8)}`,
    lineNo: null,
    componentItemId: 0,
    component: null,
    qty: 1,
    lossRate: 0,
    phantom: false,
    remark: null,
    substitutes: [],
  }
}

function fromDetail(bom: BomDetail): LineDraft[] {
  return bom.lines.map((line, index) => ({
    key: `line-${line.id ?? index}`,
    lineNo: line.lineNo,
    componentItemId: line.componentItemId,
    component: {
      id: line.componentItemId,
      code: line.componentCode ?? '',
      name: line.componentName ?? '',
    },
    qty: line.qty,
    lossRate: line.lossRate,
    phantom: line.phantom,
    remark: line.remark,
    substitutes: line.substitutes.map((substitute) => ({
      id: substitute.itemId,
      code: substitute.itemCode ?? '',
      name: substitute.itemName ?? '',
      priority: substitute.priority,
    })),
  }))
}

export default function BomEditor({ initial, onSaved, onClose }: BomEditorProps) {
  const { t } = useTranslation()

  const [item, setItem] = useState<LookupOption | null>(
    initial && initial.itemId
      ? { id: initial.itemId, code: initial.itemCode ?? '', name: initial.itemName ?? '' }
      : null,
  )
  const [version, setVersion] = useState(initial?.version ?? 'V1')
  const [baseQty, setBaseQty] = useState(initial?.baseQty ?? 1)
  const [effectiveFrom, setEffectiveFrom] = useState(initial?.effectiveFrom ?? '')
  const [effectiveTo, setEffectiveTo] = useState(initial?.effectiveTo ?? '')
  const [remark, setRemark] = useState(initial?.remark ?? '')
  const [lines, setLines] = useState<LineDraft[]>(initial ? fromDetail(initial) : [newLine(0)])
  const [copyVersion, setCopyVersion] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const locked = initial !== null && initial.status !== 'DRAFT'

  const searchItems = useCallback(async (query: string): Promise<LookupOption[]> => {
    const options = await itemOptions(query, 20)
    return options.map((option) => ({ id: option.id, code: option.code, name: option.name }))
  }, [])

  const patchLine = (key: string, changes: Partial<LineDraft>) => {
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...changes } : line)))
  }

  const payload = useMemo<BomRequest>(
    () => ({
      itemId: item?.id ?? 0,
      version,
      baseQty,
      effectiveFrom: effectiveFrom || null,
      effectiveTo: effectiveTo || null,
      remark: remark || null,
      lines: lines
        .filter((line) => line.componentItemId > 0)
        .map((line) => ({
          lineNo: line.lineNo,
          componentItemId: line.componentItemId,
          qty: line.qty,
          lossRate: line.lossRate,
          phantom: line.phantom,
          remark: line.remark,
          substitutes: line.substitutes.map((substitute, index) => ({
            itemId: substitute.id,
            priority: substitute.priority ?? index + 1,
            remark: null,
          })),
        })),
    }),
    [item, version, baseQty, effectiveFrom, effectiveTo, remark, lines],
  )

  const run = async (action: () => Promise<BomDetail>) => {
    setBusy(true)
    setError(null)
    try {
      const saved = await action()
      onSaved(saved)
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setBusy(false)
    }
  }

  const save = () =>
    run(() => (initial ? updateBom(initial.id, payload) : createBom(payload)))

  const remove = async () => {
    if (!initial) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await deleteBom(initial.id)
      onClose()
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      {error ? <div className="form-error">{error}</div> : null}
      {locked ? (
        <div className="form-error" style={{ borderColor: 'rgba(168,106,18,0.35)', borderLeftColor: 'var(--amber)', background: 'rgba(168,106,18,0.07)', color: 'var(--amber)' }}>
          {t('bom.hint.locked', { status: t(`status.revision.${initial?.status}`) })}
        </div>
      ) : null}

      <div className="form-grid form-grid--3">
        <div className="field span-2">
          <label htmlFor="bom-item">{t('bom.field.item')}</label>
          <Lookup
            id="bom-item"
            value={item}
            onChange={setItem}
            search={searchItems}
            disabled={locked || initial !== null}
            placeholder={t('bom.placeholder.item')}
          />
          <span className="hint">{t('bom.hint.item')}</span>
        </div>
        <div className="field">
          <label htmlFor="bom-version">{t('bom.field.version')}</label>
          <input
            id="bom-version"
            className="input"
            value={version}
            maxLength={16}
            disabled={locked}
            onChange={(event) => setVersion(event.target.value.toUpperCase())}
          />
        </div>
        <div className="field">
          <label htmlFor="bom-base">{t('bom.field.baseQty')}</label>
          <input
            id="bom-base"
            className="input input--num"
            type="number"
            min={0.000001}
            step="0.001"
            value={baseQty}
            disabled={locked}
            onChange={(event) => setBaseQty(Number(event.target.value) || 1)}
          />
          <span className="hint">{t('bom.hint.baseQty')}</span>
        </div>
        <div className="field">
          <label htmlFor="bom-from">{t('bom.field.effectiveFrom')}</label>
          <input
            id="bom-from"
            className="input"
            type="date"
            value={effectiveFrom ?? ''}
            disabled={locked}
            onChange={(event) => setEffectiveFrom(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="bom-to">{t('bom.field.effectiveTo')}</label>
          <input
            id="bom-to"
            className="input"
            type="date"
            value={effectiveTo ?? ''}
            disabled={locked}
            onChange={(event) => setEffectiveTo(event.target.value)}
          />
        </div>
      </div>

      <div className="form-section">{t('bom.section.lines')}</div>
      {lines.map((line, index) => (
        <div
          key={line.key}
          className={line.phantom ? 'bom-line bom-line--phantom' : 'bom-line'}
        >
          <div className="bom-line__head">
            <div className="bom-line__no">#{line.lineNo ?? (index + 1) * 10}</div>
            <div className="field">
              <label>{t('bom.field.component')}</label>
              <Lookup
                value={line.component}
                onChange={(option) =>
                  patchLine(line.key, {
                    component: option,
                    componentItemId: option?.id ?? 0,
                  })
                }
                search={searchItems}
                disabled={locked}
                placeholder={t('bom.placeholder.component')}
              />
            </div>
            <div className="field">
              <label>{t('bom.field.qty')}</label>
              <input
                className="input input--num"
                type="number"
                min={0.000001}
                step="0.001"
                value={line.qty}
                disabled={locked}
                onChange={(event) =>
                  patchLine(line.key, { qty: Number(event.target.value) || 0 })
                }
              />
            </div>
            <div className="field">
              <label>{t('bom.field.lossRate')}</label>
              <input
                className="input input--num"
                type="number"
                min={0}
                max={100}
                step="0.01"
                value={line.lossRate}
                disabled={locked}
                onChange={(event) =>
                  patchLine(line.key, { lossRate: Number(event.target.value) || 0 })
                }
              />
            </div>
            <div className="field">
              <label>{t('bom.field.phantom')}</label>
              <label className="check" style={{ height: 32 }}>
                <input
                  type="checkbox"
                  checked={line.phantom}
                  disabled={locked}
                  onChange={(event) => patchLine(line.key, { phantom: event.target.checked })}
                />
                <span className="hint">{t('bom.field.phantomShort')}</span>
              </label>
            </div>
            <button
              type="button"
              className="line-table__remove"
              title={t('common.remove')}
              disabled={locked}
              onClick={() => setLines((current) => current.filter((entry) => entry.key !== line.key))}
            >
              ✕
            </button>
          </div>

          <div className="bom-line__sub">
            <label>{t('bom.field.substitutes')}</label>
            <div>
              {line.substitutes.length > 0 ? (
                <div className="chip-set" style={{ marginBottom: 6 }}>
                  {line.substitutes.map((substitute) => (
                    <span key={substitute.id} className="chip">
                      <span className="mono" style={{ fontSize: 10 }}>
                        {substitute.priority}
                      </span>
                      {substitute.code} · {substitute.name}
                      {locked ? null : (
                        <button
                          type="button"
                          onClick={() =>
                            patchLine(line.key, {
                              substitutes: line.substitutes.filter(
                                (entry) => entry.id !== substitute.id,
                              ),
                            })
                          }
                        >
                          ✕
                        </button>
                      )}
                    </span>
                  ))}
                </div>
              ) : null}
              {locked ? null : (
                <Lookup
                  value={null}
                  onChange={(option) => {
                    if (!option) {
                      return
                    }
                    if (
                      option.id === line.componentItemId ||
                      line.substitutes.some((entry) => entry.id === option.id)
                    ) {
                      return
                    }
                    patchLine(line.key, {
                      substitutes: [
                        ...line.substitutes,
                        { ...option, priority: line.substitutes.length + 1 },
                      ],
                    })
                  }}
                  search={searchItems}
                  placeholder={t('bom.placeholder.substitute')}
                />
              )}
            </div>
          </div>

          <div className="bom-line__sub">
            <label>{t('common.remark')}</label>
            <input
              className="input"
              value={line.remark ?? ''}
              maxLength={255}
              disabled={locked}
              onChange={(event) => patchLine(line.key, { remark: event.target.value || null })}
            />
          </div>
        </div>
      ))}

      {locked ? null : (
        <div className="line-actions">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setLines((current) => [...current, newLine(current.length)])}
          >
            + {t('bom.action.addLine')}
          </button>
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
              onClick={() => run(() => copyBom(initial.id, copyVersion))}
            >
              {t('bom.action.copy')}
            </button>
            {initial.status === 'RELEASED' ? (
              <button
                type="button"
                className="btn btn--danger btn--sm"
                disabled={busy}
                onClick={() => run(() => obsoleteBom(initial.id))}
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
            <button type="button" className="btn btn--sm" disabled={busy} onClick={save}>
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
                  const saved = initial ? await updateBom(initial.id, payload) : await createBom(payload)
                  const released = await releaseBom(saved.id)
                  onSaved(released)
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

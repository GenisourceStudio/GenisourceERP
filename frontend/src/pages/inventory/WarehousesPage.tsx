import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ConfirmDialog, Drawer } from '../../components/Overlay'
import { RecordTag } from '../../components/Tags'
import { ApiError } from '../../shared/api/client'
import {
  changeLocationStatus,
  changeWarehouseStatus,
  createLocation,
  listLocations,
  listWarehouses,
  type LocationView,
  type WarehouseView,
} from '../../shared/api/inventory'
import { useAuthStore } from '../../shared/auth/store'
import WarehouseForm from './WarehouseForm'

/**
 * Warehouses and their bins.
 *
 * <p>One screen for both, because a bin cannot be set up without its warehouse and
 * the two are always read together: "where is it?" is answered by the pair.
 */
export default function WarehousesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.user?.permissions)
  const canWrite = permissions?.includes('inventory:stock:write') ?? false

  const [editing, setEditing] = useState<WarehouseView | 'new' | null>(null)
  const [locationTarget, setLocationTarget] = useState<WarehouseView | null>(null)
  const [deactivating, setDeactivating] = useState<WarehouseView | null>(null)

  const warehousesQuery = useQuery({
    queryKey: ['warehouses'],
    queryFn: listWarehouses,
  })

  const locationsQuery = useQuery({
    queryKey: ['locations', locationTarget?.id],
    queryFn: () => listLocations(locationTarget!.id),
    enabled: locationTarget !== null,
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['warehouses'] })
    void queryClient.invalidateQueries({ queryKey: ['locations'] })
    void queryClient.invalidateQueries({ queryKey: ['stock'] })
  }

  const deactivate = useMutation({
    mutationFn: (id: number) => changeWarehouseStatus(id, 'INACTIVE'),
    onSuccess: invalidate,
  })

  const columns: Column<WarehouseView>[] = [
    {
      key: 'code',
      header: t('inventory.field.code'),
      width: '120px',
      render: (warehouse) => <span className="mono">{warehouse.code}</span>,
    },
    {
      key: 'name',
      header: t('inventory.field.name'),
      render: (warehouse) => (
        <div>
          <div style={{ fontWeight: 600 }}>{warehouse.name}</div>
          {warehouse.address ? <div className="hint">{warehouse.address}</div> : null}
        </div>
      ),
    },
    {
      key: 'type',
      header: t('inventory.field.warehouseType'),
      width: '120px',
      render: (warehouse) => (
        <span className="tag tag--neutral">{t(`warehouseType.${warehouse.warehouseType}`)}</span>
      ),
    },
    {
      key: 'locations',
      header: t('inventory.field.locations'),
      align: 'right',
      width: '90px',
      render: (warehouse) => warehouse.locationCount,
    },
    {
      key: 'stock',
      header: t('inventory.field.stockItems'),
      align: 'right',
      width: '110px',
      render: (warehouse) => warehouse.stockItemCount,
    },
    {
      key: 'status',
      header: t('common.status'),
      width: '90px',
      render: (warehouse) => <RecordTag status={warehouse.status} />,
    },
    {
      key: 'actions',
      header: '',
      width: '150px',
      align: 'right',
      render: (warehouse) => (
        <div className="row-actions">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={(event) => {
              event.stopPropagation()
              setLocationTarget(warehouse)
            }}
          >
            {t('inventory.action.locations')}
          </button>
          {warehouse.status === 'ACTIVE' ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={(event) => {
                event.stopPropagation()
                setDeactivating(warehouse)
              }}
            >
              {t('common.deactivate')}
            </button>
          ) : null}
        </div>
      ),
    },
  ]

  return (
    <>
      <div className="toolbar">
        <div className="toolbar__spacer" />
        {canWrite ? (
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => setEditing('new')}
          >
            {t('inventory.action.createWarehouse')}
          </button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        rows={warehousesQuery.data ?? []}
        rowKey={(warehouse) => warehouse.id}
        loading={warehousesQuery.isLoading}
        error={warehousesQuery.error instanceof ApiError ? warehousesQuery.error.message : null}
        emptyTitle={t('inventory.emptyWarehouses')}
        emptyBody={t('inventory.emptyWarehousesBody')}
        onRowClick={(warehouse) => setEditing(warehouse)}
      />

      <Drawer
        open={editing !== null}
        title={editing === 'new' ? t('inventory.action.createWarehouse') : t('inventory.action.editWarehouse')}
        subtitle={editing === 'new' ? t('inventory.hint.createWarehouse') : editing?.code}
        onClose={() => setEditing(null)}
      >
        <WarehouseForm
          initial={editing === 'new' ? null : editing}
          onSaved={() => {
            invalidate()
            setEditing(null)
          }}
          onCancel={() => setEditing(null)}
        />
      </Drawer>

      <Drawer
        open={locationTarget !== null}
        title={t('inventory.action.locations')}
        subtitle={locationTarget ? `${locationTarget.code} · ${locationTarget.name}` : ''}
        onClose={() => setLocationTarget(null)}
      >
        <LocationList
          warehouse={locationTarget}
          rows={locationsQuery.data ?? []}
          loading={locationsQuery.isLoading}
          canWrite={canWrite}
          onChanged={invalidate}
        />
      </Drawer>

      <ConfirmDialog
        open={deactivating !== null}
        title={t('common.deactivate')}
        body={
          deactivating
            ? t('inventory.confirm.deactivateWarehouse', { code: deactivating.code })
            : ''
        }
        danger
        busy={deactivate.isPending}
        onCancel={() => setDeactivating(null)}
        onConfirm={() => {
          if (deactivating) {
            deactivate.mutate(deactivating.id)
          }
          setDeactivating(null)
        }}
      />
    </>
  )
}

interface LocationListProps {
  warehouse: WarehouseView | null
  rows: LocationView[]
  loading: boolean
  canWrite: boolean
  onChanged: () => void
}

function LocationList({ warehouse, rows, loading, canWrite, onChanged }: LocationListProps) {
  const { t } = useTranslation()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const add = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!warehouse) {
      return
    }
    setSaving(true)
    setError(null)
    try {
      await createLocation({ warehouseId: warehouse.id, code, name })
      setCode('')
      setName('')
      onChanged()
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  const toggle = async (location: LocationView) => {
    try {
      await changeLocationStatus(
        location.id,
        location.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      )
      onChanged()
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.firstMessage : t('common.saveFailed'))
    }
  }

  return (
    <div>
      {error ? <div className="form-error">{error}</div> : null}

      {loading ? <p className="hint">{t('common.loading')}</p> : null}
      {!loading && rows.length === 0 ? (
        <p className="hint">{t('inventory.emptyLocations')}</p>
      ) : null}

      <div className="line-list" style={{ marginBottom: 18 }}>
        {rows.map((location) => (
          <div className="line-list__row" key={location.id}>
            <span className="mono" style={{ width: 90 }}>
              {location.code}
            </span>
            <span style={{ flex: 1 }}>{location.name}</span>
            <RecordTag status={location.status} />
            {canWrite ? (
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={() => void toggle(location)}
              >
                {location.status === 'ACTIVE' ? t('common.deactivate') : t('common.activate')}
              </button>
            ) : null}
          </div>
        ))}
      </div>

      {canWrite ? (
        <form onSubmit={add} className="form-grid">
          <div className="field">
            <label htmlFor="location-code">{t('inventory.field.code')}</label>
            <input
              id="location-code"
              className="input"
              value={code}
              required
              maxLength={32}
              onChange={(event) => setCode(event.target.value.toUpperCase())}
            />
          </div>
          <div className="field">
            <label htmlFor="location-name">{t('inventory.field.name')}</label>
            <input
              id="location-name"
              className="input"
              value={name}
              required
              maxLength={128}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="field" style={{ alignSelf: 'end' }}>
            <button type="submit" className="btn btn--primary" disabled={saving}>
              {saving ? t('common.saving') : t('inventory.action.addLocation')}
            </button>
          </div>
        </form>
      ) : null}
    </div>
  )
}

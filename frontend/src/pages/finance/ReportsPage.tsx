import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'

import DataTable, { type Column } from '../../components/DataTable'
import { ApiError } from '../../shared/api/client'
import {
  balanceSheet,
  cashFlow,
  incomeStatement,
  trialBalance,
  type AccountLedger,
  type BalanceSheet,
  type CashFlow,
  type IncomeStatement,
  type StatementLine,
  type TrialBalanceRow,
} from '../../shared/api/ledger'
import { formatMoney } from '../../shared/format'

type Statement = 'balance' | 'income' | 'cash'

/**
 * The three statements and the trial balance.
 *
 * <p>All four read the same ledger, and they are on one screen because a reader who
 * is checking the books wants to see them together: a balance sheet that does not
 * balance next to a trial balance that does is a question the screen should answer
 * without being asked twice.
 *
 * <p>The period is one control for all of them, because they must be read over the
 * same dates — a profit figure for a different period than the balance sheet would
 * be a worse mistake than no figure at all.
 */
export default function ReportsPage() {
  const { t } = useTranslation()
  const today = new Date().toISOString().slice(0, 10)
  const yearStart = `${new Date().getFullYear()}-01-01`
  const [from, setFrom] = useState(yearStart)
  const [to, setTo] = useState(today)
  const [statement, setStatement] = useState<Statement>('balance')

  const trialQuery = useQuery({
    queryKey: ['finance', 'trial-balance', from, to],
    queryFn: () => trialBalance(from, to),
  })
  const balanceQuery = useQuery({
    queryKey: ['finance', 'balance-sheet', to],
    queryFn: () => balanceSheet(to),
  })
  const incomeQuery = useQuery({
    queryKey: ['finance', 'income-statement', from, to],
    queryFn: () => incomeStatement(from, to),
  })
  const cashQuery = useQuery({
    queryKey: ['finance', 'cash-flow', from, to],
    queryFn: () => cashFlow(from, to),
  })

  const trial = trialQuery.data
  const balance = balanceQuery.data
  const income = incomeQuery.data
  const cash = cashQuery.data

  const trialColumns: Column<TrialBalanceRow>[] = [
    {
      key: 'code',
      header: t('ledger.field.code'),
      width: '120px',
      render: (row) => <span className="mono">{row.code}</span>,
    },
    { key: 'name', header: t('ledger.field.name'), render: (row) => row.name },
    {
      key: 'opening',
      header: t('ledger.field.opening'),
      width: '130px',
      align: 'right',
      render: (row) => (
        <span className="mono">
          {row.openingDebit ? formatMoney(row.openingDebit) : ''}
          {row.openingCredit ? ` / ${formatMoney(row.openingCredit)}` : ''}
        </span>
      ),
    },
    {
      key: 'debit',
      header: t('ledger.debit'),
      width: '130px',
      align: 'right',
      render: (row) => <span className="mono">{formatMoney(row.periodDebit)}</span>,
    },
    {
      key: 'credit',
      header: t('ledger.credit'),
      width: '130px',
      align: 'right',
      render: (row) => <span className="mono">{formatMoney(row.periodCredit)}</span>,
    },
    {
      key: 'closing',
      header: t('ledger.field.closing'),
      width: '150px',
      align: 'right',
      render: (row) => (
        <span className="mono">
          {row.closingDebit ? formatMoney(row.closingDebit) : ''}
          {row.closingCredit ? ` / ${formatMoney(row.closingCredit)}` : ''}
        </span>
      ),
    },
  ]

  return (
    <>
      <div className="toolbar">
        <label className="row gap-8" style={{ fontSize: 12, color: 'var(--muted)' }}>
          {t('ledger.field.from')}
          <input
            className="input"
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </label>
        <label className="row gap-8" style={{ fontSize: 12, color: 'var(--muted)' }}>
          {t('ledger.field.to')}
          <input
            className="input"
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </label>
        <div className="toolbar__spacer" />
        <div className="row gap-8">
          {(['balance', 'income', 'cash'] as const).map((value) => (
            <button
              key={value}
              type="button"
              className={statement === value ? 'btn btn--primary btn--sm' : 'btn btn--ghost btn--sm'}
              onClick={() => setStatement(value)}
            >
              {t(`ledger.statement.${value}`)}
            </button>
          ))}
        </div>
      </div>

      {statement === 'balance' ? (
        <>
          <BalanceSheetView sheet={balance} error={balanceQuery.error} />
          <h3 className="section-title" style={{ marginTop: 28 }}>
            {t('ledger.section.trialBalance')}
          </h3>
          <DataTable
            columns={trialColumns}
            rows={trial?.rows ?? []}
            rowKey={(row) => row.accountId}
            loading={trialQuery.isLoading}
            error={trialQuery.error instanceof ApiError ? trialQuery.error.message : null}
            emptyTitle={t('ledger.emptyReports.title')}
            emptyBody={t('ledger.emptyReports.body')}
          />
          {trial ? (
            <p className={trial.closingDifference === 0 ? 'hint' : 'form-error'}>
              {trial.closingDifference === 0
                ? t('ledger.hint.balanced')
                : t('ledger.hint.difference', {
                    difference: formatMoney(trial.closingDifference),
                  })}
            </p>
          ) : null}
        </>
      ) : null}

      {statement === 'income' ? (
        <IncomeStatementView statement={income} error={incomeQuery.error} />
      ) : null}

      {statement === 'cash' ? (
        <CashFlowView flow={cash} error={cashQuery.error} />
      ) : null}
    </>
  )
}

function BalanceSheetView({ sheet, error }: { sheet?: BalanceSheet; error: unknown }) {
  const { t } = useTranslation()
  if (error instanceof ApiError) {
    return <div className="form-error">{error.message}</div>
  }
  if (!sheet) {
    return <p className="hint">{t('common.loading')}</p>
  }
  const columns: Column<StatementLine>[] = [
    {
      key: 'code',
      header: t('ledger.field.code'),
      width: '120px',
      render: (row) => <span className="mono">{row.code}</span>,
    },
    { key: 'name', header: t('ledger.field.name'), render: (row) => row.name },
    {
      key: 'amount',
      header: t('ledger.field.amount'),
      width: '150px',
      align: 'right',
      render: (row) => <span className="mono">{formatMoney(row.amount)}</span>,
    },
  ]
  return (
    <>
      <div className="grid grid--2">
        <div>
          <h3 className="section-title">{t('ledger.section.assets')}</h3>
          <DataTable
            columns={columns}
            rows={sheet.assets}
            rowKey={(row) => row.accountId}
            emptyTitle={t('ledger.emptyReports.title')}
            emptyBody={t('ledger.emptyReports.body')}
          />
          <p className="stat__label">
            {t('ledger.field.totalAssets')}：{formatMoney(sheet.totalAssets)}
          </p>
        </div>
        <div>
          <h3 className="section-title">{t('ledger.section.claims')}</h3>
          <DataTable
            columns={columns}
            rows={[...sheet.liabilities, ...sheet.equity]}
            rowKey={(row) => row.accountId}
            emptyTitle={t('ledger.emptyReports.title')}
            emptyBody={t('ledger.emptyReports.body')}
          />
          <p className="stat__label">
            {t('ledger.field.totalLiabilities')}：{formatMoney(sheet.totalLiabilities)} ·{' '}
            {t('ledger.field.undistributed')}：{formatMoney(sheet.undistributedProfit)}
          </p>
        </div>
      </div>
      <p className={sheet.difference === 0 ? 'hint' : 'form-error'}>
        {sheet.difference === 0
          ? t('ledger.hint.balanced')
          : t('ledger.hint.difference', { difference: formatMoney(sheet.difference) })}
      </p>
    </>
  )
}

function IncomeStatementView({
  statement,
  error,
}: {
  statement?: IncomeStatement
  error: unknown
}) {
  const { t } = useTranslation()
  if (error instanceof ApiError) {
    return <div className="form-error">{error.message}</div>
  }
  if (!statement) {
    return <p className="hint">{t('common.loading')}</p>
  }
  const columns: Column<StatementLine>[] = [
    {
      key: 'code',
      header: t('ledger.field.code'),
      width: '120px',
      render: (row) => <span className="mono">{row.code}</span>,
    },
    { key: 'name', header: t('ledger.field.name'), render: (row) => row.name },
    {
      key: 'amount',
      header: t('ledger.field.amount'),
      width: '150px',
      align: 'right',
      render: (row) => <span className="mono">{formatMoney(row.amount)}</span>,
    },
  ]
  return (
    <>
      <h3 className="section-title">{t('ledger.section.revenue')}</h3>
      <DataTable
        columns={columns}
        rows={statement.revenue}
        rowKey={(row) => row.accountId}
        emptyTitle={t('ledger.emptyReports.title')}
        emptyBody={t('ledger.emptyReports.body')}
      />
      <h3 className="section-title" style={{ marginTop: 20 }}>
        {t('ledger.section.cost')}
      </h3>
      <DataTable
        columns={columns}
        rows={statement.cost}
        rowKey={(row) => row.accountId}
        emptyTitle={t('ledger.emptyReports.title')}
        emptyBody={t('ledger.emptyReports.body')}
      />
      <h3 className="section-title" style={{ marginTop: 20 }}>
        {t('ledger.section.expense')}
      </h3>
      <DataTable
        columns={columns}
        rows={statement.expense}
        rowKey={(row) => row.accountId}
        emptyTitle={t('ledger.emptyReports.title')}
        emptyBody={t('ledger.emptyReports.body')}
      />
      <p className="stat__label">
        {t('ledger.field.profit')}：{formatMoney(statement.profit)}
      </p>
    </>
  )
}

function CashFlowView({ flow, error }: { flow?: CashFlow; error: unknown }) {
  const { t } = useTranslation()
  if (error instanceof ApiError) {
    return <div className="form-error">{error.message}</div>
  }
  if (!flow) {
    return <p className="hint">{t('common.loading')}</p>
  }
  return (
    <>
      <h3 className="section-title">{t('ledger.section.cashFlow')}</h3>
      <table className="line-list">
        <thead>
          <tr>
            <th>{t('ledger.field.section')}</th>
            <th className="num">{t('ledger.field.inflow')}</th>
            <th className="num">{t('ledger.field.outflow')}</th>
            <th className="num">{t('ledger.field.net')}</th>
          </tr>
        </thead>
        <tbody>
          {flow.sections.map((section) => (
            <tr key={section.section}>
              <td>{t(`ledger.cashSection.${section.section}`)}</td>
              <td className="num mono">{formatMoney(section.inflow)}</td>
              <td className="num mono">{formatMoney(section.outflow)}</td>
              <td className="num mono">{formatMoney(section.net)}</td>
            </tr>
          ))}
          <tr>
            <td className="mono">{t('ledger.field.cashChange')}</td>
            <td className="num mono" colSpan={3}>
              {formatMoney(flow.openingCash)} → {formatMoney(flow.closingCash)}
            </td>
          </tr>
        </tbody>
      </table>
    </>
  )
}

export type { AccountLedger }

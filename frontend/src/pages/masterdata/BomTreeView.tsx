import { useTranslation } from 'react-i18next'

import type { BomTreeNode } from '../../shared/api/masterdata'

interface BomTreeProps {
  node: BomTreeNode
}

/**
 * Recursive rendering of an exploded BOM.
 *
 * <p>Shows the accumulated requirement ({@code qty}) as the primary number and the
 * per-parent quantity as a hint: when a component appears under two different parents the
 * figures that matter are "how much in total" and "how much of it comes from this parent".
 *
 * <p>Keys are built from the position in the tree, not the item id, because the same
 * component legitimately appears several times in one explosion.
 */
export default function BomTreeView({ node }: BomTreeProps) {
  return (
    <div className="tree">
      <TreeRow node={node} path="0" />
    </div>
  )
}

function TreeRow({ node, path }: BomTreeProps & { path: string }) {
  const { t } = useTranslation()
  return (
    <>
      <div className="tree__row" style={{ ['--depth' as string]: node.level }}>
        <div className="tree__label">
          {node.level > 0 ? <span className="tree__branch" /> : null}
          <strong title={node.itemName}>{node.itemName}</strong>
          <span className="tree__code">{node.itemCode}</span>
          {node.phantom ? <span className="tag">{t('bom.field.phantomShort')}</span> : null}
          {node.source === 'CYCLE' ? <span className="tag tag--inactive">CYCLE</span> : null}
          {node.bomVersion && node.level > 0 ? (
            <span className="tag tag--draft">{node.bomVersion}</span>
          ) : null}
        </div>
        <div className="tree__qty" title={t('bom.tree.total')}>
          {formatQty(node.qty)}
        </div>
        <div className="tree__qty hint" title={t('bom.tree.perParent')}>
          × {formatQty(node.perParentQty)}
        </div>
        <div className="tree__level">{node.uomCode ?? ''}</div>
      </div>
      {node.children.map((child, index) => (
        <TreeRow key={`${path}.${index}`} node={child} path={`${path}.${index}`} />
      ))}
    </>
  )
}

function formatQty(value: number): string {
  const rounded = Math.round(value * 1000) / 1000
  return rounded.toLocaleString(undefined, { maximumFractionDigits: 3 })
}

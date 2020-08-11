package stores.kg

import models.kg.KgNode
import stores.SortDirection
import stores.SortDirection.SortDirection
import stores.kg.KgNodeSortableField.KgNodeSortableField

final case class KgNodeSort(field: KgNodeSortableField, direction: SortDirection)

object KgNodeSort {
  def sort(nodes: List[KgNode], sorts: List[KgNodeSort]): List[KgNode] = {
    if (sorts.isEmpty) nodes
    else KgNodeSort.sort(nodes.sortBy(node => KgNodeSortableField.getFieldString(node, sorts.last.field))(if (sorts.last.direction == SortDirection.Ascending) Ordering[String] else Ordering[String].reverse), sorts.init)
  }
}
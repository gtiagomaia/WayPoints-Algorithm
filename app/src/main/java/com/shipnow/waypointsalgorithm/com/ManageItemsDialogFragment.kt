package com.shipnow.waypointsalgorithm.com

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.gms.maps.model.MarkerOptions
import com.shipnow.waypointsalgorithm.R

// TODO: Customize parameter argument names
const val ARG_ITEM_LIST = "item_count"

/**
 *
 * A fragment that shows a list of items as a modal bottom sheet.
 *
 * You can show this modal bottom sheet from your activity like this:
 * <pre>
 *    ManageItemsDialogFragment.newInstance(30).show(supportFragmentManager, "dialog")
 * </pre>
 */
class ManageItemsDialogFragment() : BottomSheetDialogFragment() {

    private lateinit var markers:MutableList<MarkerOptions>
    private var onDismissListener: DialogInterface.OnDismissListener? = null
    private var rvList:RecyclerView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manage_items_dialog_list_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        rvList = view.findViewById<RecyclerView>(R.id.rv_list)?.apply {
            layoutManager = LinearLayoutManager(context)

            if(::markers.isInitialized){
                adapter = ItemAdapter(markers)
            }
            val itemDecoration =
                DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL)
            addItemDecoration(itemDecoration)
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(rvList)
    }



    private inner class ItemAdapter (val markers:MutableList<MarkerOptions>) :
        RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context), parent)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.text.text = markers[position].title
        }

        override fun getItemCount(): Int {
            return markers.size
        }

        inner class ViewHolder internal constructor(
            inflater: LayoutInflater,
            parent: ViewGroup
        ) : RecyclerView.ViewHolder(
            inflater.inflate(
                R.layout.fragment_manage_items_dialog_list_dialog_item,
                parent,
                false
            )
        ) {

            internal val text: TextView = itemView.findViewById(R.id.text)
        }
    }

    private val itemTouchHelperCallback = object: ItemTouchHelper.Callback() {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            // Specify the directions of movement
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            // Notify your adapter that an item is moved from x position to y position
            markers.swap(viewHolder.adapterPosition, target.adapterPosition)
            rvList?.adapter?.notifyItemMoved(viewHolder.adapterPosition, target.adapterPosition)

            return true
        }

        override fun isLongPressDragEnabled(): Boolean {
            // true: if you want to start dragging on long press
            // false: if you want to handle it yourself
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            // Hanlde action state changes
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            // Called by the ItemTouchHelper when the user interaction with an element is over and it also completed its animation
            // This is a good place to send update to your backend about changes
            Log.i("recyclerViewChanges", "output after changes drag and drop =========")
            (recyclerView.adapter as ItemAdapter).markers.forEach {
                Log.i("recyclerViewChanges", it.title)
            }
            Log.i("listChanges", "output after changes drag and drop =========")
            markers.forEach {
                Log.i("listChanges", it.title)
            }
        }
    }





    fun setOnDismissListener(onDismissListener: DialogInterface.OnDismissListener) {
        this.onDismissListener = onDismissListener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.let {
                it.onDismiss(dialog)
        }
    }

    companion object{
        fun newInstance(list:MutableList<MarkerOptions>):ManageItemsDialogFragment = ManageItemsDialogFragment().apply {
            this.markers = list
        }
    }
}


//extension
fun <T> MutableList<T>.swap(index1: Int, index2: Int) {
    val tmp = this[index1] // 'this' corresponds to the list
    this[index1] = this[index2]
    this[index2] = tmp
}
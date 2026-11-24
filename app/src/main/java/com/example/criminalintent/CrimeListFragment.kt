package com.example.criminalintent

import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

private const val LIST_DATE_FORMAT = "EEE, MMM dd, yyyy"

class CrimeListFragment : Fragment() {

    interface Callbacks {
        fun onCrimeSelected(crimeId: UUID?)
    }

    private var callbacks: Callbacks? = null
    private lateinit var crimeRecyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private val adapter = CrimeAdapter { crime -> callbacks?.onCrimeSelected(crime.id) }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callbacks = context as? Callbacks
    }

    override fun onDetach() {
        super.onDetach()
        callbacks = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_crime_list, container, false)
        crimeRecyclerView = view.findViewById(R.id.crime_recycler_view)
        emptyView = view.findViewById(R.id.crime_list_empty)
        crimeRecyclerView.layoutManager = LinearLayoutManager(context)
        crimeRecyclerView.adapter = adapter
        view.findViewById<Button>(R.id.add_crime_button).setOnClickListener {
            callbacks?.onCrimeSelected(null)
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        CrimeRepository.get().getCrimes().observe(viewLifecycleOwner) { crimes ->
            adapter.submitList(crimes.sortedByDescending { it.date })
            emptyView.visibility = if (crimes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private class CrimeAdapter(private val onClick: (Crime) -> Unit) :
        ListAdapter<Crime, CrimeHolder>(DiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CrimeHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_crime, parent, false)
            return CrimeHolder(view, onClick)
        }

        override fun onBindViewHolder(holder: CrimeHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class CrimeHolder(view: View, private val onClick: (Crime) -> Unit) :
        RecyclerView.ViewHolder(view) {

        private val titleTextView: TextView = view.findViewById(R.id.list_crime_title)
        private val dateTextView: TextView = view.findViewById(R.id.list_crime_date)
        private val suspectTextView: TextView = view.findViewById(R.id.list_crime_suspect)
        private val solvedImageView: ImageView = view.findViewById(R.id.list_crime_solved)

        fun bind(crime: Crime) {
            val context = itemView.context
            titleTextView.text = crime.title
            dateTextView.text = DateFormat.format(LIST_DATE_FORMAT, crime.date).toString()
            suspectTextView.text = when {
                crime.suspect.isBlank() -> context.getString(R.string.crime_list_no_suspect)
                crime.suspectPhone.isBlank() ->
                    context.getString(R.string.crime_list_suspect, crime.suspect)
                else -> context.getString(
                    R.string.crime_list_suspect_phone, crime.suspect, crime.suspectPhone
                )
            }
            solvedImageView.visibility = if (crime.isSolved) View.VISIBLE else View.INVISIBLE
            itemView.setOnClickListener { onClick(crime) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Crime>() {
        override fun areItemsTheSame(oldItem: Crime, newItem: Crime) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Crime, newItem: Crime) = oldItem == newItem
    }
}

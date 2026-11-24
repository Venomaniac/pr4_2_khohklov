package com.example.criminalintent

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.format.DateFormat as AndroidDateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.util.UUID

private const val ARG_CRIME_ID = "crime_id"
private const val DATE_FORMAT = "EEE, MMM, dd"

class CrimeFragment : Fragment() {

    private val viewModel: CrimeDetailViewModel by viewModels()

    private lateinit var rootView: View
    private lateinit var titleField: EditText
    private lateinit var dateButton: Button
    private lateinit var solvedCheckBox: CheckBox
    private lateinit var suspectButton: Button
    private lateinit var suspectPhoneText: TextView
    private lateinit var callButton: Button
    private lateinit var reportButton: Button
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button


    private val pickContact =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val contactUri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && contactUri != null) {
                onContactPicked(contactUri)
            }
        }

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val contactId = viewModel.pendingContactId
            viewModel.pendingContactId = null
            if (granted && contactId != null) {
                applyPhone(contactId)
            } else if (!granted) {
                showMessage(R.string.crime_contacts_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crimeId = arguments?.getString(ARG_CRIME_ID)?.let { UUID.fromString(it) }
        viewModel.load(crimeId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_crime, container, false)
        rootView = view.findViewById(R.id.fragment_crime_root)
        titleField = view.findViewById(R.id.crime_title)
        dateButton = view.findViewById(R.id.crime_date)
        solvedCheckBox = view.findViewById(R.id.crime_solved)
        suspectButton = view.findViewById(R.id.crime_suspect)
        suspectPhoneText = view.findViewById(R.id.crime_suspect_phone)
        callButton = view.findViewById(R.id.crime_call_suspect)
        reportButton = view.findViewById(R.id.crime_report)
        saveButton = view.findViewById(R.id.crime_save)
        deleteButton = view.findViewById(R.id.crime_delete)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setFormEnabled(false)
        callButton.isEnabled = false

        titleField.doOnTextChanged { text, _, _, _ -> viewModel.crime?.title = text.toString() }

        dateButton.isEnabled = false

        solvedCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val crime = viewModel.crime ?: return@setOnCheckedChangeListener
            if (crime.isSolved != isChecked) {
                crime.isSolved = isChecked
                if (isChecked) showMessage(R.string.crime_solved_snackbar)
            }
        }

        suspectButton.setOnClickListener {
            val pickContactIntent =
                Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            try {
                pickContact.launch(pickContactIntent)
            } catch (e: ActivityNotFoundException) {
                // Приложения адресной книги нет — кнопка бесполезна.
                suspectButton.isEnabled = false
                showMessage(R.string.crime_no_contacts_app)
            }
        }

        callButton.setOnClickListener {
            val phone = viewModel.crime?.suspectPhone.orEmpty()
            if (phone.isBlank()) {
                showMessage(R.string.crime_no_suspect_phone)
                return@setOnClickListener
            }
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
            try {
                startActivity(dialIntent)
            } catch (e: ActivityNotFoundException) {
                showMessage(R.string.crime_no_dialer_app)
            }
        }

        reportButton.setOnClickListener {
            val report = getCrimeReport() ?: return@setOnClickListener
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crime_report_subject))
            }.also { intent ->
                startActivity(Intent.createChooser(intent, getString(R.string.send_report)))
            }
        }

        saveButton.setOnClickListener {
            val crime = viewModel.crime ?: return@setOnClickListener
            if (crime.title.isBlank()) {
                showMessage(R.string.crime_title_required)
            } else {
                CrimeRepository.get().saveCrime(crime)
                parentFragmentManager.popBackStack()
            }
        }

        deleteButton.setOnClickListener {
            val crime = viewModel.crime ?: return@setOnClickListener
            CrimeRepository.get().deleteCrime(crime)
            parentFragmentManager.popBackStack()
        }

        viewModel.crimeLiveData.observe(viewLifecycleOwner) { crime ->
            titleField.setText(crime.title)
            dateButton.text = DateFormat.getDateInstance(DateFormat.FULL).format(crime.date)
            solvedCheckBox.isChecked = crime.isSolved
            solvedCheckBox.jumpDrawablesToCurrentState()
            deleteButton.visibility = if (viewModel.isExisting) View.VISIBLE else View.GONE
            setFormEnabled(true)
            updateUI()
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        titleField.isEnabled = enabled
        solvedCheckBox.isEnabled = enabled
        suspectButton.isEnabled = enabled
        reportButton.isEnabled = enabled
        saveButton.isEnabled = enabled
        deleteButton.isEnabled = enabled
    }

    private fun onContactPicked(contactUri: Uri) {
        val crime = viewModel.crime ?: return
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME
        )
        requireActivity().contentResolver.query(contactUri, projection, null, null, null)?.use {
            if (!it.moveToFirst()) return
            val contactId = it.getString(0) ?: return
            val name = it.getString(1) ?: return

            crime.suspect = name
            crime.suspectPhone = ""
            updateUI()

            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                applyPhone(contactId)
            } else {
                viewModel.pendingContactId = contactId
                requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun applyPhone(contactId: String) {
        val crime = viewModel.crime ?: return
        val phoneColumn = ContactsContract.CommonDataKinds.Phone.NUMBER
        val phone = requireActivity().contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(phoneColumn),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

        if (phone.isNullOrBlank()) {
            showMessage(R.string.crime_no_suspect_phone)
        } else {
            crime.suspectPhone = phone
            updateUI()
        }
    }

    private fun updateUI() {
        val crime = viewModel.crime ?: return
        suspectButton.text = if (crime.suspect.isBlank()) {
            getString(R.string.crime_suspect_text)
        } else {
            crime.suspect
        }

        if (crime.suspectPhone.isBlank()) {
            suspectPhoneText.visibility = View.GONE
        } else {
            suspectPhoneText.visibility = View.VISIBLE
            suspectPhoneText.text = getString(R.string.crime_suspect_phone, crime.suspectPhone)
        }
        callButton.isEnabled = crime.suspectPhone.isNotBlank()
    }

    private fun getCrimeReport(): String? {
        val crime = viewModel.crime ?: return null
        val solvedString = if (crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }

        val dateString = AndroidDateFormat.format(DATE_FORMAT, crime.date).toString()

        val suspectText = if (crime.suspect.isBlank()) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(R.string.crime_report_suspect, crime.suspect)
        }

        return getString(R.string.crime_report, crime.title, dateString, solvedString, suspectText)
    }

    private fun showMessage(resId: Int) {
        Snackbar.make(rootView, resId, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance(crimeId: UUID?): CrimeFragment = CrimeFragment().apply {
            arguments = Bundle().apply { putString(ARG_CRIME_ID, crimeId?.toString()) }
        }
    }
}

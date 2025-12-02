package com.simba.meetingnotification.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.simba.meetingnotification.ui.contact.BeforeTemplateDestination
import com.simba.meetingnotification.ui.contact.ContactReadyForSms
import com.simba.meetingnotification.ui.contact.ContactsSearchScreenViewModel
import com.simba.meetingnotification.ui.home.HomeDestination
import com.simba.meetingnotification.ui.home.InstructionsDestination
import com.simba.meetingnotification.ui.services.ServiceAction
import com.simba.meetingnotification.ui.services.SmsSendingService
import com.simba.meetingnotification.ui.services.SmsSendingServiceInteractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val TAG = MainActivity::class.simpleName

//Haupt Activity. Startet direkt nach Application.
class MainActivity : AppCompatActivity(), SmsSendingServiceInteractor {

    //region Companion object
    companion object { // Begleitendes statisches Objekt für Konfigurationskonstanten
        private const val REQUEST_CODE_FOR_ALL_NEEDED_PERMISSIONS = 1
    }
    //endregion

    //region Properties
    private val contactBuffer by viewModels<ContactsSearchScreenViewModel> { AppViewModelProvider.Factory }     // ViewModel für die Kontaktsuche
    private lateinit var smsService: SmsSendingService                 // Später zu initialisierender SMS-Dienst
    private lateinit var applicationMain: MeetingNotificationApplication
    private lateinit var analytics: FirebaseAnalytics
    private var retriesLeftToInitializeService = 30
    //endregion

    //region Override methods (main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = Firebase.analytics
        supportActionBar?.hide()
        Log.d(TAG,"onCreate() - MainActivity")
        FirebaseCrashlytics.getInstance().log("onCreate() - MainActivity")

        //We receive this Intent always when the Notification is opened through the user.
        val destinationWhenClickNotification = intent?.getStringExtra("destination")
        Log.d(TAG,"Intent destination = $destinationWhenClickNotification")
        FirebaseCrashlytics.getInstance().log("Intent destination = $destinationWhenClickNotification")

        applicationMain = application as MeetingNotificationApplication

        val instructionRepoState = applicationMain.instructionReadStateRepository

        val instructionAllreadyAccepted = runBlocking {
            instructionRepoState.get().first()
        }

        //First destination of UI Screen.
        var initialDestination: String = HomeDestination.route

        //Shows allways the instruction screen until the user acknowledge it.
        if (!instructionAllreadyAccepted){
            initialDestination = InstructionsDestination.route
            Log.d(TAG,"The app instruction was not read!")
            FirebaseCrashlytics.getInstance().log("The app instruction was not read!")
        }else if (destinationWhenClickNotification != null){
            initialDestination = BeforeTemplateDestination.route
        }
        Log.d(TAG,"instructionRepoState = $instructionAllreadyAccepted")
        FirebaseCrashlytics.getInstance().log("instructionRepoState = $instructionAllreadyAccepted")

        // Verknüpft das ViewModel mit dem Dienst-Interface, das wiederum in das Mainactivity eingebunden ist. Wichtige Schnittstelle, weil im viewmodel kein service erstellt werden darf.
        contactBuffer.smsServiceInteractor = this

        //This is the main entry to the nav-graph and to the composables.
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                NotificationApp(
                    viewModel = contactBuffer,
                    initialDestination = initialDestination
                )
            }
        }

        //Nötig um Intent zurückzusetzen, das diese Activity geöffnet hat (Notification Click), weil bei bilschirmrotation (onCreate() trigger) ansonsten die Route immer genutzt werden würde.
        intent?.replaceExtras(Intent())
    }



    // Wird beim Start der Aktivität aufgerufen
    override fun onStart() {
        super.onStart()
        Log.d(TAG,"onStart() - MainActivity")
        FirebaseCrashlytics.getInstance().log("onStart() - MainActivity")

        checkAndRequestPermissions()

        val serviceIntent = Intent(this, SmsSendingService::class.java)
        startService(serviceIntent)
        waitForServiceAndInit()
        Log.i(TAG,"SDK Version = ${Build.VERSION.SDK_INT}")
    }

    // Wird beim Fortsetzen der Aktivität aufgerufen
    override fun onResume() {
        super.onResume()
        Log.d(TAG,"onResume() - MainActivity")
        FirebaseCrashlytics.getInstance().log("onResume() - MainActivity")
    }

    // Wird beim Pausieren der Aktivität aufgerufen
    override fun onPause() {
        super.onPause()
        Log.d(TAG,"onPause() - MainActivity")
        FirebaseCrashlytics.getInstance().log("onPause() - MainActivity")
    }

    // Wird beim Stoppen der Aktivität aufgerufen
    override fun onStop() {
        super.onStop()
        if (SmsSendingService.getInstance()?.getMessageQueue()?.isEmpty() == true){
            stopService(Intent(this,SmsSendingService::class.java))
            Log.d(TAG, "Service stopped in onStop() because queue was empty")
        }

        contactBuffer._isLoading.value = true
        Log.d(TAG,"onStop() - MainActivity")
        FirebaseCrashlytics.getInstance().log("onStop() - MainActivity")
    }

    // Wird beim Zerstören der Aktivität aufgerufen
    override fun onDestroy() {
        super.onDestroy()
        contactBuffer.smsServiceInteractor = null                      // Entfernt die Verbindung zwischen Dienst und ViewModel
        Log.d(TAG,"onDestroy() - MainActivity")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d(TAG,"onRestart() - MainActivity")
        FirebaseCrashlytics.getInstance().log("onRestart() - MainActivity")
    }
    //endregion

    //region Override methods (other)
    //Action to push contacts in servive or to send message to all contacts or to get Contacts from ServiceQueue.
    override fun performServiceActionToAddOrSend(action: ServiceAction, contacts: List<ContactReadyForSms>)
    {
        if (::smsService.isInitialized && action == ServiceAction.PushContact && contacts.isNotEmpty()) { // Prüft, ob der Dienst gebunden und die Aktion gültig ist. Action to insert Contact to Queue.
            val allContacts = mutableListOf<ContactReadyForSms>()                                // Liste für alle Kontakte
            contacts.forEach { contact ->                                                        // Fügt die Kontakte zur Liste hinzu
                allContacts.add(contact)
            }
            smsService.addMessageToQueue(allContacts)                  // Fügt die Kontakte zur Warteschlange des Dienstes hinzu
        } else if (::smsService.isInitialized && action == ServiceAction.SendMessage) { //Action to Send Messages
            smsService.showMessageSendDialog(
                this@MainActivity,
                smsService.getMessageQueue().takeIf { it.isNotEmpty() }?.map { it.fullName }.toString()
            ) { accepted ->
                if (accepted) {
                    smsService.sendNextMessage(this@MainActivity)
                }
            }
        }
    }

    override fun performServiceActionToGetContactFromQueue(action: ServiceAction): List<Int> {
        return if (::smsService.isInitialized && SmsSendingService.getInstance() != null && action == ServiceAction.GetContactsFromQueue)
            smsService.getContactsInSmsQueueWithId()
        else
            emptyList()
    }

    override fun performServiceActionToRemoveFromQueue(action: ServiceAction, contactId: Int) {
        if (action == ServiceAction.DeleteContactFromQueue && ::smsService.isInitialized){
            smsService.removeContactFromQueue(contactId)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_FOR_ALL_NEEDED_PERMISSIONS) {

            val deniedPermissions = permissions.filterIndexed { index, _ -> grantResults[index] != PackageManager.PERMISSION_GRANTED }

            if (deniedPermissions.isEmpty() || (deniedPermissions.size == 1 && deniedPermissions[0] == Manifest.permission.POST_NOTIFICATIONS) ) {
                contactBuffer.loadContactsWrapper(this)
                contactBuffer.loadCalenderWrapper(this)
                Log.d(TAG, "Alle Berechtigungen akzeptiert oder nur Benachrichtigungen abgelehnt")
                FirebaseCrashlytics.getInstance().log("Alle Berechtigungen akzeptiert oder nur Benachrichtigungen abgelehnt")
            } else {
                val shouldShowRationale = deniedPermissions.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }
                Log.d(TAG,"Denied permissions : $deniedPermissions")
                FirebaseCrashlytics.getInstance().log("Denied permissions : $deniedPermissions")
                FirebaseCrashlytics.getInstance().recordException(IllegalStateException("Some permissions denied"))

                if (shouldShowRationale) {
                    showPermissionExplanationDialog(deniedPermissions)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.permission_permanently_denied_title))
                        .setMessage(getString(R.string.permission_permanently_denied_message))
                        .setPositiveButton(getString(R.string.permission_go_to_settings)) { _, _ ->
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", packageName, null)
                            startActivity(intent)
                        }
                        .setNegativeButton(getString(R.string.permission_dialog_negative), null)
                        .show()
                }
            }
        }
    }
    //endregion

    //region Methods
    //Trying to initialize the service with a Looper, because it wont start immediately in the release build.
    private fun waitForServiceAndInit() {
        val service = SmsSendingService.getInstance()
        if (service != null) {
            service.initialize(
                applicationMain.container.contactRepository,
                applicationMain.container.eventRepository,
                applicationMain.container.dateMessageSendRepository
            )
            smsService = service
            Log.i(TAG,"Service has ben started succesfully()")
            FirebaseCrashlytics.getInstance().log("Service has ben started succesfully()")

            retriesLeftToInitializeService = 30
        }else if (retriesLeftToInitializeService > 0) {
            Log.e(TAG,"Service start failed. New try() attemps left: $retriesLeftToInitializeService")
            retriesLeftToInitializeService--
            Handler(Looper.getMainLooper()).postDelayed({ waitForServiceAndInit() }, 300)
        } else {
            Log.w(TAG,"Sms service initialization attempts timeout, service is not initialized()")
            FirebaseCrashlytics.getInstance().log("Sms service initialization attempts timeout, service is not initialized()")
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (!isPermissionGranted(Manifest.permission.READ_CONTACTS)) {
            permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        }
        if (!isPermissionGranted(Manifest.permission.READ_CALENDAR)) {
            permissionsToRequest.add(Manifest.permission.READ_CALENDAR)
        }
        if (!isPermissionGranted(Manifest.permission.SEND_SMS)) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && !isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissionsToRequest.size == 1 && permissionsToRequest[0] == Manifest.permission.POST_NOTIFICATIONS) {
            contactBuffer.loadContactsWrapper(this)
            contactBuffer.loadCalenderWrapper(this)
            Log.d(TAG, "Permissions granted for all except the Post_Notifications. Loading Calender and Contacts...")
        } else if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(), // Alle benötigten permissions in einem Array.
                REQUEST_CODE_FOR_ALL_NEEDED_PERMISSIONS //  Alle Permissions unter gleichem Code,
            )
        } else {
            // Nur wenn ALLE Berechtigungen erteilt wurden, App starten
            contactBuffer.loadContactsWrapper(this)
            contactBuffer.loadCalenderWrapper(this)
            Log.d(TAG, "Permissions granted for all. Loading data...")
        }
    }

    private fun showPermissionExplanationDialog(ungrantedPermissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_dialog_title))
            .setMessage(getString(R.string.permission_dialog_message))
            .setPositiveButton(getString(R.string.permission_dialog_positive)) { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    ungrantedPermissions.toTypedArray(),
                    REQUEST_CODE_FOR_ALL_NEEDED_PERMISSIONS
                )
            }
            .setNegativeButton(getString(R.string.permission_dialog_negative), null)
            .show()
    }

    private fun isPermissionGranted(permission: String): Boolean {    // Überprüft, ob eine Berechtigung erteilt wurde
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
    //endregion
}





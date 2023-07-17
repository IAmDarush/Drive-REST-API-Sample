/**
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.drive.sample.driveapimigration

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList

/**
 * The main [Activity] for the Drive API migration sample app.
 */
class MainActivity : AppCompatActivity() {

  private var mDriveServiceHelper: DriveServiceHelper? = null
  private var mOpenFileId: String? = null
  private var mFileTitleEditText: EditText? = null
  private var mDocContentEditText: EditText? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Store the EditText boxes to be updated when files are opened/created/modified.
    mFileTitleEditText = findViewById(R.id.file_title_edittext)
    mDocContentEditText = findViewById(R.id.doc_content_edittext)

    // Set the onClick listeners for the button bar.
    findViewById<View>(R.id.open_btn).setOnClickListener { openFilePicker() }
    findViewById<View>(R.id.create_btn).setOnClickListener { createFile() }
    findViewById<View>(R.id.save_btn).setOnClickListener { saveFile() }
    findViewById<View>(R.id.query_btn).setOnClickListener { query() }

    // Authenticate the user. For most apps, this should be done when the user performs an
    // action that requires Drive access rather than in onCreate.
    requestSignIn()
  }

  @Deprecated("use the Activity Result API")
  public override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
    when (requestCode) {
      REQUEST_CODE_SIGN_IN       -> if (resultCode == RESULT_OK && resultData != null) {
        handleSignInResult(resultData)
      }
      REQUEST_CODE_OPEN_DOCUMENT -> if (resultCode == RESULT_OK && resultData != null) {
        resultData.data?.let { uri ->
          openFileFromFilePicker(uri)
        }
      }
    }
    super.onActivityResult(requestCode, resultCode, resultData)
  }

  /**
   * Starts a sign-in activity using [.REQUEST_CODE_SIGN_IN].
   */
  private fun requestSignIn() {
    Log.d(TAG, "Requesting sign-in")
    val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestEmail()
      .requestScopes(Scope(DriveScopes.DRIVE_FILE))
      .build()
    val client = GoogleSignIn.getClient(this, signInOptions)

    // The result of the sign-in Intent is handled in onActivityResult.
    startActivityForResult(client.signInIntent, REQUEST_CODE_SIGN_IN)
  }

  /**
   * Handles the `result` of a completed sign-in activity initiated from [ ][.requestSignIn].
   */
  private fun handleSignInResult(result: Intent) {
    GoogleSignIn.getSignedInAccountFromIntent(result)
      .addOnSuccessListener { googleAccount: GoogleSignInAccount ->
        Log.d(TAG, "Signed in as " + googleAccount.email)

        // Use the authenticated account to sign in to the Drive service.
        val credential = GoogleAccountCredential.usingOAuth2(
          this, setOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = googleAccount.account
        val googleDriveService = Drive.Builder(
          NetHttpTransport(),
          GsonFactory(),
          credential
        )
          .setApplicationName("Drive API Migration")
          .build()

        // The DriveServiceHelper encapsulates all REST API and SAF functionality.
        // Its instantiation is required before handling any onClick actions.
        mDriveServiceHelper = DriveServiceHelper(googleDriveService)
      }
      .addOnFailureListener { exception: Exception? ->
        Log.e(TAG, "Unable to sign in.", exception)
      }
  }

  /**
   * Opens the Storage Access Framework file picker using [.REQUEST_CODE_OPEN_DOCUMENT].
   */
  private fun openFilePicker() {
    if (mDriveServiceHelper != null) {
      Log.d(TAG, "Opening file picker.")
      val pickerIntent = mDriveServiceHelper!!.createFilePickerIntent()
      // The result of the SAF Intent is handled in onActivityResult.
      startActivityForResult(pickerIntent, REQUEST_CODE_OPEN_DOCUMENT)
    }
  }

  /**
   * Opens a file from its `uri` returned from the Storage Access Framework file picker
   * initiated by [.openFilePicker].
   */
  private fun openFileFromFilePicker(uri: Uri) {
    if (mDriveServiceHelper != null) {
      Log.d(TAG, "Opening " + uri.path)
      mDriveServiceHelper!!.openFileUsingStorageAccessFramework(contentResolver, uri)
        .addOnSuccessListener { nameAndContent: Pair<String, String> ->
          val name = nameAndContent.first
          val content = nameAndContent.second
          mFileTitleEditText?.setText(name)
          mDocContentEditText?.setText(content)

          // Files opened through SAF cannot be modified.
          setReadOnlyMode()
        }
        .addOnFailureListener { exception: Exception? ->
          Log.e(TAG, "Unable to open file from picker.", exception)
        }
    }
  }

  /**
   * Creates a new file via the Drive REST API.
   */
  private fun createFile() {
    if (mDriveServiceHelper != null) {
      Log.d(TAG, "Creating a file.")
      mDriveServiceHelper!!.createFile()
        .addOnSuccessListener { fileId: String -> readFile(fileId) }
        .addOnFailureListener { exception: Exception? ->
          Log.e(TAG, "Couldn't create file.", exception)
        }
    }
  }

  /**
   * Retrieves the title and content of a file identified by `fileId` and populates the UI.
   */
  private fun readFile(fileId: String) {
    if (mDriveServiceHelper != null) {
      Log.d(TAG, "Reading file $fileId")
      mDriveServiceHelper!!.readFile(fileId)
        .addOnSuccessListener { nameAndContent: Pair<String, String> ->
          val name = nameAndContent.first
          val content = nameAndContent.second
          mFileTitleEditText?.setText(name)
          mDocContentEditText?.setText(content)
          setReadWriteMode(fileId)
        }
        .addOnFailureListener { exception: Exception? ->
          Log.e(TAG, "Couldn't read file.", exception)
        }
    }
  }

  /**
   * Saves the currently opened file created via [.createFile] if one exists.
   */
  private fun saveFile() {
    if (mDriveServiceHelper != null && mOpenFileId != null) {
      Log.d(TAG, "Saving $mOpenFileId")
      val fileName = mFileTitleEditText?.text.toString()
      val fileContent = mDocContentEditText?.text.toString()
      mDriveServiceHelper!!.saveFile(mOpenFileId, fileName, fileContent)
        .addOnFailureListener { exception: Exception? ->
          Log.e(TAG, "Unable to save file via REST.", exception)
        }
    }
  }

  /**
   * Queries the Drive REST API for files visible to this app and lists them in the content view.
   */
  private fun query() {
    if (mDriveServiceHelper != null) {
      Log.d(TAG, "Querying for files.")
      mDriveServiceHelper!!.queryFiles()
        .addOnSuccessListener { fileList: FileList ->
          val builder = StringBuilder()
          for (file in fileList.files) {
            builder.append(file.name).append("\n")
          }
          val fileNames = builder.toString()
          mFileTitleEditText?.setText(getString(R.string.lbl_file_list))
          mDocContentEditText?.setText(fileNames)
          setReadOnlyMode()
        }
        .addOnFailureListener { exception: Exception? ->
          Log.e(
            TAG,
            "Unable to query files.",
            exception
          )
        }
    }
  }

  /**
   * Updates the UI to read-only mode.
   */
  private fun setReadOnlyMode() {
    mFileTitleEditText?.isEnabled = false
    mDocContentEditText?.isEnabled = false
    mOpenFileId = null
  }

  /**
   * Updates the UI to read/write mode on the document identified by `fileId`.
   */
  private fun setReadWriteMode(fileId: String) {
    mFileTitleEditText?.isEnabled = true
    mDocContentEditText?.isEnabled = true
    mOpenFileId = fileId
  }

  companion object {
    private const val TAG = "MainActivity"
    private const val REQUEST_CODE_SIGN_IN = 1
    private const val REQUEST_CODE_OPEN_DOCUMENT = 2
  }
}
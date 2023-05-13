package com.example.myforumapplication.ui.screen.writemessage

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myforumapplication.data.Post
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.UUID

sealed interface WritePostUiState {
    object Init : WritePostUiState
    object LoadingImageUpload : WritePostUiState
    object LoadingPostUpload : WritePostUiState
    object PostUploadSuccess : WritePostUiState
    object ImageUploadSuccess : WritePostUiState
    data class ErrorDuringPostUpload(val error: String?) : WritePostUiState
    data class ErrorDuringImageUpload(val error: String?) : WritePostUiState
}

class WritePostViewModel : ViewModel() {
    companion object {
        const val COLLECTION_POSTS = "posts"
    }

    var writePostUiState: WritePostUiState by mutableStateOf(WritePostUiState.Init)
    private var auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun uploadPost(title: String, postBody: String, imgUrl: String = "") {
        writePostUiState = WritePostUiState.LoadingPostUpload

        val myPost = Post(
            uid = auth.currentUser!!.uid,
            author = auth.currentUser!!.email!!,
            title = title,
            body = postBody,
            imgUrl = imgUrl
        )

        val postsCollection = FirebaseFirestore.getInstance().collection(
            COLLECTION_POSTS
        )
        postsCollection.add(myPost)
            .addOnSuccessListener {
                writePostUiState = WritePostUiState.PostUploadSuccess
            }
            .addOnFailureListener {
                writePostUiState = WritePostUiState.ErrorDuringPostUpload(it.message)
            }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    public fun uploadPostImage(
        contentResolver: ContentResolver, imageUri: Uri,
        title: String, postBody: String
    ) {
        viewModelScope.launch {
            writePostUiState = WritePostUiState.LoadingImageUpload

            val source = ImageDecoder.createSource(contentResolver, imageUri)
            val bitmap = ImageDecoder.decodeBitmap(source)

            val baos = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val imageInBytes = baos.toByteArray()

            // prepare the empty file in the cloud
            val storageRef = FirebaseStorage.getInstance().reference
            val newImage = URLEncoder.encode(UUID.randomUUID().toString(), "UTF-8") + ".jpg"
            val newImagesRef = storageRef.child("images/$newImage")

            // upload the jpeg byte array to the created empty file
            newImagesRef.putBytes(imageInBytes)
                .addOnFailureListener { e ->
                    writePostUiState = WritePostUiState.ErrorDuringImageUpload(e.message)
                }.addOnSuccessListener {
                    writePostUiState = WritePostUiState.ImageUploadSuccess

                    newImagesRef.downloadUrl.addOnCompleteListener { task -> // the public URL of the image is: task.result.toString()
                        uploadPost(title, postBody, task.result.toString())
                    }
                }
        }
    }

}
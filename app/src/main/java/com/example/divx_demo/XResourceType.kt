package com.example.divx_demo

import android.net.Uri

sealed class XResourceType () {
    abstract val xid: String
}


data class XVideo(override val xid: String, val uri:Uri): XResourceType()

data class XAudio(override val xid: String, val uri:Uri): XResourceType()

data class XImage(override val xid: String, val uri:Uri): XResourceType()

data class XWebpage(override val xid: String, val url: Uri): XResourceType()

data class XQuiz(override val xid: String): XResourceType()
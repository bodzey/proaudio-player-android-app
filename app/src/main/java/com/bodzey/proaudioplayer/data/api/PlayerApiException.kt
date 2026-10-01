package com.bodzey.proaudioplayer.data.api

import java.io.IOException

class PlayerApiException(
    val statusCode: Int,
    message: String,
    cause: IOException? = null,
) : IOException(message, cause)

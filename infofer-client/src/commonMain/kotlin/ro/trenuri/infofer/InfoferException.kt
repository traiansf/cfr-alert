package ro.trenuri.infofer

open class InfoferException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InfoferParseException(message: String) : InfoferException(message)
class InfoferNetworkException(message: String, cause: Throwable? = null) : InfoferException(message, cause)

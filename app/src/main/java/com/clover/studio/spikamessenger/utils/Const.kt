package com.clover.studio.spikamessenger.utils

class Const {
    class Navigation {
        companion object {
            const val COUNTRY_CODE: String = "county_code"
            const val PHONE_NUMBER: String = "phone_number"
            const val DEVICE_ID: String = "device_id"
            const val PHONE_NUMBER_HASHED = "phone_number_hashed"
            const val USER_PROFILE = "user_profile"
            const val ROOM_DATA = "room_data"
            const val GO_ACCOUNT_CREATION = "go_account_creation"
            const val SELECTED_USERS = "selected_users"
            const val ROOM_ID = "roomId"
            const val NOTE_ID = "noteId"
            const val NOTES_DETAILS = "notesDetails"
            const val NOTES_NAME = "notesName"
            const val SEARCH_MESSAGE_ID = "searchMessageId"
        }
    }

    class Service {
        companion object {
            const val UPLOAD_SERVICE_ID = 1111
        }
    }

    class Time {
        companion object {
            const val DAY = 24 * 60 * 60
        }
    }

    class UserActions {
        companion object {
            const val DELETE_MESSAGE_ME = "user"
            const val DELETE_MESSAGE_ALL = "all"
            const val DOWNLOAD_FILE = "download_file"
            const val CANCEL_UPLOAD = "upload_cancel"
            const val MESSAGE_ACTION = "action"
            const val MESSAGE_REPLY = "reply"
            const val MESSAGE_PREVIEW = "preview"
            const val RESEND_MESSAGE = "resend"
            const val USER_REMOVE = "remove_user"
            const val USER_OPTIONS = "user_options"
            const val SHOW_MESSAGE_REACTIONS = "show_message_reactions"
            const val NAVIGATE_TO_MEDIA_FRAGMENT = "navigate_to_media_fragment"
            const val NAVIGATE_TO_USER_DETAILS = "navigate_to_user_details"
            const val ACTION_LEFT = "left"
            const val ACTION_RIGHT = "right"
        }
    }

    class MediaActions {
        companion object {
            const val MEDIA_SHOW_BARS = "media_show_bars"
            const val MEDIA_DOWNLOAD = "media_download"
        }
    }

    class IntentExtras {
        companion object {
            const val ROOM_ID_EXTRA = "room_id_extra"
        }
    }

    class FileExtensions {
        companion object {
            const val PDF = "pdf"
            const val ZIP = "zip"
            const val RAR = "rar"
            const val MP3 = "mp3"
            const val WAW = "waw"
            const val JPG = "jpg"
            const val GIF = "gif"
            const val AUDIO = "audio/mpeg"
        }
    }

    class JsonFields {
        companion object {
            const val CODE: String = "code"
            const val DEVICE_ID = "deviceId"
            const val TELEPHONE_NUMBER = "telephoneNumber"
            const val TELEPHONE_NUMBER_HASHED = "telephoneNumberHashed"
            const val COUNTRY_CODE = "countryCode"
            const val PUSH_TOKEN = "pushToken"

            // Mute/Unmute
            const val SUCCESS = "success"

            // SSE
            const val NEW_MESSAGE = "NEW_MESSAGE"
            const val UPDATE_MESSAGE = "UPDATE_MESSAGE"
            const val DELETE_MESSAGE = "DELETE_MESSAGE"
            const val NEW_MESSAGE_RECORD = "NEW_MESSAGE_RECORD"
            const val DELETE_MESSAGE_RECORD = "DELETE_MESSAGE_RECORD"
            const val NEW_ROOM = "NEW_ROOM"
            const val UPDATE_ROOM = "UPDATE_ROOM"
            const val DELETE_ROOM = "DELETE_ROOM"
            const val USER_UPDATE = "USER_UPDATE"
            const val SEEN_ROOM = "SEEN_ROOM"

            // Mime types
            const val IMAGE = "image/*"
            const val IMAGE_PREFIX = "image/"
            const val IMAGE_JPEG = "image/jpeg"
            const val VIDEO_MP4 = "video/mp4"
            const val VIDEO_PREFIX = "video/"
            const val FILE_PREFIX = "application/pdf"
            const val FILE = "*/*"
            const val TEXT_PREFIX = "text/plain"

            // File types
            const val AVATAR_TYPE = "avatar"
            const val FILE_TYPE = "file"
            const val AUDIO_TYPE = "audio"
            const val VIDEO_TYPE = "video"
            const val TEXT_TYPE = "text"
            const val IMAGE_TYPE = "image"
            const val GIF_TYPE = "image/gif"
            const val GIF = "gif"
            const val SYSTEM_TYPE = "system"
            const val SVG_TYPE = "svg"
            const val AVI_TYPE = "avi"
            const val MOV_TYPE = "quicktime"

            // File upload
            const val CHUNK = "chunk"
            const val OFFSET = "offset"
            const val TOTAL = "total"
            const val SIZE = "size"
            const val MIME_TYPE = "mimeType"
            const val FILE_NAME = "fileName"
            const val TYPE = "type"
            const val FILE_HASH = "fileHash"
            const val CLIENT_ID = "clientId"
            const val FILE_ID = "fileId"
            const val THUMB_ID = "thumbId"
            const val LOCAL_ID = "localId"
            const val METADATA = "metaData"
            const val WIDTH = "width"
            const val HEIGHT = "height"
            const val DURATION = "duration"

            // Chat
            const val ROOM_ID = "roomId"
            const val ROOM_IDS = "roomIds"
            const val NAME = "name"
            const val AVATAR_FILE_ID = "avatarFileId"
            const val USER_IDS = "userIds"
            const val ACTION = "action"
            const val ADMIN_USER_IDS = "adminUserIds"
            const val PRIVATE = "private"
            const val GROUP = "group"
            const val BODY = "body"
            const val MESSAGE_IDS = "messageIds"
            const val REACTION = "reaction"
            const val THUMBNAIL_DATA = "thumbnailData"

            // Room actions
            const val ADD_GROUP_USERS = "addGroupUsers"
            const val REMOVE_GROUP_USERS = "removeGroupUsers"
            const val ADD_GROUP_ADMINS = "addGroupAdmins"
            const val REMOVE_GROUP_ADMINS = "removeGroupAdmins"
            const val CHANGE_GROUP_NAME = "changeGroupName"
            const val CHANGE_GROUP_AVATAR = "changeGroupAvatar"

            // Reply
            const val REPLY_ID = "replyId"

            // Message details
            const val SEEN = "seen"
            const val DELIVERED = "delivered"
            const val SENT = "sent"
        }
    }

    class Networking {
        companion object {
            const val TIMEOUT_SECONDS: Long = 30

            // Sync Data
            const val API_SYNC_MESSAGES = "api/messenger/messages/sync/{lastUpdate}"
            const val API_SYNC_MESSAGE_RECORDS = "api/messenger/message-records/sync/{lastUpdate}"
            const val API_SYNC_USERS = "api/messenger/users/sync/{lastUpdate}"
            const val API_SYNC_ROOMS = "api/messenger/rooms/sync/{lastUpdate}"

            // Reactions
            const val API_POST_REACTION = "api/messenger/message-records"
            const val API_DELETE_REACTION = "api/messenger/message-records/{id}"

            // User settings
            const val API_AUTH = "api/messenger/auth"
            const val API_VERIFY_CODE = "api/messenger/auth/verify"
            const val API_CONTACTS = "api/messenger/contacts"  // Fix
            const val API_UPDATE_USER = "api/messenger/me" // Fix
            const val API_SSE_STREAM = "api/sse"
            const val API_UPDATE_TOKEN = "api/messenger/device"

            // Files
            const val API_GET_FILE_FROM_ID = "api/upload/files/"
            const val API_UPLOAD_FILE = "api/upload/files" // Fix
            const val API_VERIFY_FILE = "api/upload/files/verify" // Fix

            // Messages
            const val API_POST_MESSAGE = "api/messenger/messages"
            const val API_MESSAGE_DELIVERED = "api/messenger/messages/delivered" // Fix
            const val API_GET_PAGE_METADATA = "api/messenger/messages/get-thumbnail"

            // Room
            const val API_GET_ROOM_BY_ID = "api/messenger/rooms/users/{userId}"
            const val API_POST_NEW_ROOM = "api/messenger/rooms" // Fix
            const val API_UPDATE_ROOM = "api/messenger/rooms/{roomId}" // Fix
            const val API_MUTE_ROOM = "api/messenger/rooms/{roomId}/mute"
            const val API_UNMUTE_ROOM = "api/messenger/rooms/{roomId}/unmute"
            const val API_PIN_ROOM = "api/messenger/rooms/{roomId}/pin"
            const val API_UNPIN_ROOM = "api/messenger/rooms/{roomId}/unpin"
            const val API_MESSAGES_SEEN = "api/messenger/messages/{roomId}/seen"
            const val API_UNREAD_COUNT = "api/messenger/rooms/unread-count"
            const val API_FORWARD = "api/messenger/messages/forward"
            const val API_USER_SETTINGS = "api/messenger/me/settings"
            const val API_UPDATE_MESSAGE = "api/messenger/messages/{id}"
            const val API_LEAVE_ROOM = "api/messenger/rooms/{id}/leave"

            // Notes
            const val API_NOTES = "api/messenger/notes/roomId/{roomId}"
            const val API_MANAGE_NOTE = "api/messenger/notes/{id}"

            // Block
            const val API_BLOCK = "api/messenger/blocks"
            const val API_DELETE_BLOCK = "api/messenger/blocks/{id}"
            const val API_DELETE_BLOCK_FOR_USER = "api/messenger/blocks/userId/{userId}"
            const val API_SETTINGS = "api/messenger/settings"

            // Share
            const val API_SHARE_MEDIA = "api/messenger/messages/share"

            // Queries
            const val ROOM_ID = "roomId"
            const val CONTACTS = "contacts"
            const val USER_ID = "userId"
            const val MESSAGE_ID = "messageId"
            const val LAST_UPDATE = "lastUpdate"
            const val ID = "id"
            const val TARGET = "target"
            const val IS_LAST_PAGE = "isLastPage"
            const val PAGE = "page"
            const val URL = "url"
        }
    }

    class Headers {
        companion object {
            const val ACCESS_TOKEN = "accesstoken"
            const val OS_NAME = "os-name"
            const val OS_VERSION = "os-version"
            const val DEVICE_NAME = "device-name"
            const val APP_VERSION = "app-version"
            const val LANGUAGE = "lang"

            // Field values
            const val ANDROID = "android"
        }
    }

    class PrefsData {
        companion object {
            const val SHARED_PREFS_NAME = "app_general_prefs"
            const val TOKEN = "token"
            const val USER_CONTACTS = "user_contacts"
            const val USER_ID = "user_id"
            const val ACCOUNT_CREATED = "account_created"
            const val PUSH_TOKEN = "pushToken"
            const val NEW_USER = "newUser"
            const val DATA_SYNCED = "dataSynced"
            const val FIRST_START = "firstStart"
            const val PHONE_NUMBER = "phoneNumber"
            const val DEVICE_ID = "deviceId"
            const val COUNTRY_CODE = "countryCode"
            const val REGISTERED = "registered"
            const val BLOCKED_USERS = "blocked_users"
            const val IS_TEAM_MODE = "is_team_mode"

            // Sync
            const val MESSAGE_RECORD_SYNC = "message_record_sync"
            const val MESSAGE_SYNC = "message_sync"
            const val USER_SYNC = "user_sync"
            const val ROOM_SYNC = "room_sync"
            const val CONTACT_SYNC = "contact_sync"

            // Theme
            const val THEMES = "themes"

            const val NOTIFICATION_REPLY_KEY = "key_text_reply"
            const val NOTIFICATION_REPLY_DATA = "reply_data"
        }
    }

    class UserData {
        companion object {
            const val DISPLAY_NAME: String = "displayName"
            const val AVATAR_FILE_ID: String = "avatarFileId"
        }
    }

    class Urls {
        companion object {
            const val TERMS_AND_CONDITIONS = "https://clover.studio/projects/spika/spika-messenger-terms-and-coditions/"
        }
    }

    class Themes {
        companion object {
            const val MINT_THEME = "mint"
            const val NEON_THEME = "neon"
            const val BASIC_THEME = "basic"
            const val BASIC_THEME_NIGHT = "basic_night"
        }
    }

    class SystemMessages {
        companion object {
            const val CREATED_NOTE = "created_note"
            const val UPDATED_NOTE = "updated_note"
            const val DELETED_NOTE = "deleted_note"

            const val CREATED_GROUP = "created_group"
            const val USER_LEFT_GROUP = "user_left_group"
            const val UPDATED_GROUP_NAME = "updated_group_name"
            const val UPDATED_GROUP_AVATAR = "updated_group_avatar"
            const val UPDATED_GROUP_MEMBERS = "updated_group_members"
            const val ADDED_GROUP_MEMBERS = "added_group_members"
            const val REMOVED_GROUP_MEMBERS = "removed_group_members"
            const val ADDED_GROUP_ADMINS = "added_group_admins"
            const val REMOVED_GROUP_ADMINS = "removed_group_admins"
        }
    }

    class Giphy {
        companion object {
            const val GIPHY_BOTTOM_SHEET_TAG = "gifs_dialog"
        }
    }
}

package com.niki914.zafiro.chat.routing

// Protects cloud bypass from performing a partial or destructive interpretation of a compound request.
import org.junit.Assert.*
import org.junit.Test

class DirectCommandTest {
    @Test fun hebrewAndEnglishSimpleCommandsRouteLocally() {
        assertEquals(DirectCommand.Open("whatsapp"), DirectCommand.parse("Please open WhatsApp!"))
        assertEquals(DirectCommand.Open("וואטסאפ"), DirectCommand.parse("תפתח לי את וואטסאפ"))
        assertEquals(DirectCommand.Back, DirectCommand.parse("חזור אחורה"))
    }
    @Test fun compoundAndSensitiveMessagingRequestsRequireTheAgent() {
        assertNull(DirectCommand.parse("open WhatsApp and send mom good night"))
        assertNull(DirectCommand.parse("פתח וואטסאפ ושלח לאמא לילה טוב"))
        assertNull(DirectCommand.parse("שלח הודעת וואטסאפ לאמא עם המילים לילה טוב"))
    }
    @Test fun webAndAmbiguousNavigationAreNotLaunchedAsApps() {
        assertNull(DirectCommand.parse("open https://example.com"))
        assertNull(DirectCommand.parse("go back then delete it"))
        assertEquals(DirectCommand.Volume(-1), DirectCommand.parse("הנמך ווליום"))
    }
}

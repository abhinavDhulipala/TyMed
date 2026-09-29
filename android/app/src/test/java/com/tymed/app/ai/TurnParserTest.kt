package com.tymed.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// org.json.JSONObject (used by parseTurn) is a stub-only class in the plain android.jar local
// unit tests otherwise link against — Robolectric supplies a real shadow implementation instead.
@RunWith(RobolectricTestRunner::class)
class TurnParserTest {

    @Test
    fun `parses a plain tool-call object`() {
        val result = parseTurn("""{"tool": "get_todays_doses", "arguments": {}}""")
        assertEquals(ParsedTurn.Tool("get_todays_doses", emptyMap()), result)
    }

    @Test
    fun `defaults arguments to empty map when omitted`() {
        val result = parseTurn("""{"tool": "get_todays_doses"}""")
        assertEquals(ParsedTurn.Tool("get_todays_doses", emptyMap()), result)
    }

    @Test
    fun `defaults arguments to empty map when arguments is not an object`() {
        val result = parseTurn("""{"tool": "get_todays_doses", "arguments": "oops"}""")
        assertEquals(ParsedTurn.Tool("get_todays_doses", emptyMap()), result)
    }

    @Test
    fun `unwraps a json code fence`() {
        val result = parseTurn("```json\n{\"tool\": \"get_todays_doses\", \"arguments\": {}}\n```")
        assertEquals(ParsedTurn.Tool("get_todays_doses", emptyMap()), result)
    }

    @Test
    fun `extracts the JSON object out of surrounding prose`() {
        val result = parseTurn("Sure, let me check that. {\"tool\": \"get_todays_doses\", \"arguments\": {}} Just a sec.")
        assertEquals(ParsedTurn.Tool("get_todays_doses", emptyMap()), result)
    }

    @Test
    fun `parses a plain reply object`() {
        val result = parseTurn("""{"reply": "You have 2 doses left today."}""")
        assertEquals(ParsedTurn.Reply("You have 2 doses left today."), result)
    }

    @Test
    fun `rejects invalid JSON`() {
        val raw = "not json at all"
        assertEquals(ParsedTurn.Unparseable(raw), parseTurn(raw))
    }

    @Test
    fun `rejects a JSON array`() {
        val raw = """["tool", "get_todays_doses"]"""
        assertEquals(ParsedTurn.Unparseable(raw), parseTurn(raw))
    }

    @Test
    fun `rejects an object with neither tool nor reply`() {
        val raw = """{"message": "hello"}"""
        assertEquals(ParsedTurn.Unparseable(raw), parseTurn(raw))
    }

    @Test
    fun `rejects a non-string reply`() {
        val raw = """{"reply": 42}"""
        assertEquals(ParsedTurn.Unparseable(raw), parseTurn(raw))
    }
}

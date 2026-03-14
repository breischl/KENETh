package dev.breischl.keneth.web.debugger

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLStyleElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.Option

/**
 * Example text for each message type. The hex representation is computed from
 * these at runtime via [MessageCodec.encodeText], so we only maintain one side.
 */
private val EXAMPLES: Map<String, String> = linkedMapOf(
    "DemandParameters" to """
        DemandParameters
        voltage: 400.0
        current: 32.0
        voltageLimits: 500.0
        currentLimits: 100.0
        powerLimit: 10000.0
        duration: 3600000
    """.trimIndent(),

    "Ping" to "Ping",
    
    "SessionParameters" to """
        SessionParameters
        identity: my-device
        type: charger
        version: 1.0
        name: My Charger
        provider: energy-co
    """.trimIndent(),

    "SoftDisconnect" to """
        SoftDisconnect
        reconnect: true
        reason: firmware update
    """.trimIndent(),

    "StorageParameters" to """
        StorageParameters
        soc: 80.0
        socTarget: 100.0
        socTargetTime: 7200000
        capacity: 60000.0
    """.trimIndent(),

    "SupplyParameters" to """
        SupplyParameters
        voltageLimits.min: 48.0
        voltageLimits.max: 52.0
        currentLimits.min: 1.0
        currentLimits.max: 100.0
        powerLimit: 10000.0
        voltage: 48.0
    """.trimIndent(),
)

/** CSS scoped under #keneth-message-debugger to avoid collisions with the host site. */
private val SCOPED_CSS = """
    #keneth-message-debugger .dbg-intro {
        margin-bottom: 16px;
    }
    #keneth-message-debugger .dbg-row {
        display: flex;
        gap: 0;
        align-items: stretch;
    }
    #keneth-message-debugger .dbg-panel {
        flex: 1;
        display: flex;
        flex-direction: column;
    }
    #keneth-message-debugger .dbg-panel h3 {
        margin: 0 0 8px 0;
        font-size: 1.1em;
    }
    #keneth-message-debugger textarea {
        width: 100%;
        height: 15em;
        font-family: monospace;
        font-size: 13px;
        padding: 8px;
        border: 1px solid #ccc;
        border-radius: 4px;
        resize: vertical;
        box-sizing: border-box;
    }
    #keneth-message-debugger .dbg-buttons {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        gap: 12px;
        padding: 0 16px;
        min-width: 100px;
    }
    #keneth-message-debugger .dbg-buttons button {
        padding: 8px 16px;
        cursor: pointer;
        white-space: nowrap;
        min-width: 90px;
    }
    #keneth-message-debugger .dbg-example-bar {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 16px;
    }
    #keneth-message-debugger .dbg-example-bar select {
        padding: 4px 8px;
        font-size: 14px;
    }
    #keneth-message-debugger .dbg-example-btn {
        padding: 4px 12px;
        cursor: pointer;
    }
    #keneth-message-debugger .dbg-error {
        color: #c00;
        font-size: 13px;
        margin-top: 4px;
        min-height: 1.2em;
    }
""".trimIndent()

/** Initializes the message debugger page. Called from [dev.breischl.keneth.web.main]. */
fun initDebugger() {
    val container = document.getElementById("keneth-message-debugger") as? HTMLElement ?: run {
        console.error("Could not find #keneth-message-debugger container element")
        return
    }

    // --- Inject scoped styles ---
    val style = (document.createElement("style") as HTMLStyleElement).apply {
        textContent = SCOPED_CSS
    }
    document.head?.appendChild(style)

    // --- Header text ---
    val intro = (document.createElement("div") as HTMLDivElement).apply {
        className = "dbg-intro"
        innerHTML = """
            <p>Decode hex-encoded EP frames to readable text, or encode text back to hex.
            The hex side expects full <strong>frame</strong> bytes (magic header + type ID + CBOR payload),
            because the message type is identified by the type ID in the frame &mdash; it isn't part of
            the CBOR payload itself. The text side shows the message type name, plus the decoded message fields.
            Use the "Paste Example" function at the top to insert example messages.
            </p>
        """.trimIndent()
    }
    container.appendChild(intro)

    // --- Example selector bar ---
    val exampleSelect = (document.createElement("select") as HTMLSelectElement).apply {
        for (name in EXAMPLES.keys) {
            appendChild(Option(name, name))
        }
    }
    val pasteBtn = createButton("Paste Example")
    pasteBtn.className = "dbg-example-btn"

    val exampleBar = (document.createElement("div") as HTMLDivElement).apply {
        className = "dbg-example-bar"
        appendChild(exampleSelect)
        appendChild(pasteBtn)
    }
    container.appendChild(exampleBar)

    // --- Hex panel (left) ---
    val hexInput = createTextArea("Paste hex-encoded EP frame here...")
    val hexError = createErrorDiv()

    val hexPanel = (document.createElement("div") as HTMLDivElement).apply {
        className = "dbg-panel"
        val heading = document.createElement("h3").apply { textContent = "Hex (Frame Bytes)" }
        appendChild(heading)
        appendChild(hexInput)
        appendChild(hexError)
    }

    // --- Text panel (right) ---
    val textInput = createTextArea(
        "Type message text here...\ne.g.:\nSessionParameters\nidentity: my-device\ntype: charger"
    )
    val textError = createErrorDiv()

    val textPanel = (document.createElement("div") as HTMLDivElement).apply {
        className = "dbg-panel"
        val heading = document.createElement("h3").apply { textContent = "Text (Decoded Message)" }
        appendChild(heading)
        appendChild(textInput)
        appendChild(textError)
    }

    // --- Center button column ---
    val decodeBtn = createButton("Decode \u2192")
    val encodeBtn = createButton("\u2190 Encode")
    val buttonColumn = (document.createElement("div") as HTMLDivElement).apply {
        className = "dbg-buttons"
        appendChild(decodeBtn)
        appendChild(encodeBtn)
    }

    // --- Assemble row: [hex panel] [buttons] [text panel] ---
    val row = (document.createElement("div") as HTMLDivElement).apply {
        className = "dbg-row"
        appendChild(hexPanel)
        appendChild(buttonColumn)
        appendChild(textPanel)
    }
    container.appendChild(row)

    // --- Paste Example: fills both panels ---
    pasteBtn.addEventListener("click", {
        hexError.textContent = ""
        textError.textContent = ""
        val selectedType = exampleSelect.value
        val exampleText = EXAMPLES[selectedType] ?: return@addEventListener

        textInput.value = exampleText
        val hexResult = MessageCodec.encodeText(exampleText)
        if (hexResult.isSuccess) {
            hexInput.value = hexResult.getOrThrow()
        } else {
            hexError.textContent = "Failed to encode example: ${hexResult.exceptionOrNull()?.message}"
        }
    })

    // --- Decode: hex → text ---
    decodeBtn.addEventListener("click", {
        hexError.textContent = ""
        textError.textContent = ""
        val hex = hexInput.value.trim()
        if (hex.isEmpty()) {
            hexError.textContent = "Enter hex data to decode"
            return@addEventListener
        }

        val result = MessageCodec.decodeHex(hex)
        if (result.isSuccess) {
            textInput.value = result.getOrThrow()
        } else {
            hexError.textContent = result.exceptionOrNull()?.message ?: "Decode failed"
        }
    })

    // --- Encode: text → hex ---
    encodeBtn.addEventListener("click", {
        hexError.textContent = ""
        textError.textContent = ""
        val text = textInput.value.trim()
        if (text.isEmpty()) {
            textError.textContent = "Enter message text to encode"
            return@addEventListener
        }

        val result = MessageCodec.encodeText(text)
        if (result.isSuccess) {
            hexInput.value = result.getOrThrow()
        } else {
            textError.textContent = result.exceptionOrNull()?.message ?: "Encode failed"
        }
    })
}

private fun createTextArea(placeholder: String): HTMLTextAreaElement {
    return (document.createElement("textarea") as HTMLTextAreaElement).apply {
        this.placeholder = placeholder
    }
}

private fun createButton(label: String): HTMLButtonElement {
    return (document.createElement("button") as HTMLButtonElement).apply {
        textContent = label
    }
}

private fun createErrorDiv(): HTMLDivElement {
    return (document.createElement("div") as HTMLDivElement).apply {
        className = "dbg-error"
    }
}

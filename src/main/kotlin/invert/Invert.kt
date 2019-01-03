import io.kloudformation.Inverter
import io.kloudformation.mapToStandard
import java.io.File

/**
 * Basic example to show Kloudformation Inverter generate
 * a Kloudformation Kotlin representation of a given AWS CloudFormation Template.
 * Generated Kotlin can be found in `$outputDir/io/kloudformation/stack/MyStack.kt`
 */


const val inputCFT = "example.json"
const val outputDir = "out"

fun main(args: Array<String>){
    try {
        val fileText = File(inputCFT).readText()
        val standard = mapToStandard(fileText)
        Inverter.invert(standard).writeTo(File("out"))
    }
    catch(e: Exception){
        if(e.message != null) error(e.message.toString()) else e.printStackTrace()
    }
}
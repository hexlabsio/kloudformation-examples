package io.kloudformation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.kloudformation.model.KloudFormationTemplate
import org.junit.Test

class Sandbox{
    @Test
    fun play(){
        val template = KloudFormationTemplate.create {

        }

        println(ObjectMapper(YAMLFactory())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(KloudFormationTemplate.NamingStrategy())
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(template))
    }
}
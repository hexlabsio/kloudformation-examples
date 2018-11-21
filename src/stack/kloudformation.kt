import io.kloudformation.KloudFormation
import io.kloudformation.StackBuilder
import io.kloudformation.Value
import io.kloudformation.json
import io.kloudformation.model.iam.PrincipalType
import io.kloudformation.model.iam.action
import io.kloudformation.model.iam.allResources
import io.kloudformation.model.iam.policyDocument
import io.kloudformation.property.ec2.securitygroup.Ingress
import io.kloudformation.resource.cloudformation.customResource
import io.kloudformation.resource.ec2.instance
import io.kloudformation.resource.ec2.securityGroup
import io.kloudformation.resource.ec2.vPC
import io.kloudformation.resource.sns.topic
import io.kloudformation.resource.sns.topicPolicy


class Stack: StackBuilder {
    override fun KloudFormation.create() {
        val topic = topic()
        val standardCustomResource = customResource(
                logicalName = "DatabaseInitializer",
                serviceToken = +"arn:aws::xxxx:xxx",
                metadata = json(mapOf(
                        "SomeKey" to "SomeValue"
                ))
        ).asCustomResource(properties = mapOf(
                "A" to "B",
                "C" to topic.ref()
        ))
        val customNameCustomResource = customResource(
                logicalName = "DatabaseInitializer2",
                serviceToken = +"arn:aws::xxxx:xxx",
                metadata = json(mapOf(
                        "SomeKey" to "SomeValue"
                ))
        ).asCustomResource("DBInit", emptyMap<String, String>())
    }
}
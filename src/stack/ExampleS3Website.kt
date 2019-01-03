import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.iam.PolicyDocument
import io.kloudformation.model.iam.policyDocument
import io.kloudformation.resource.s3.Bucket
import io.kloudformation.resource.s3.BucketPolicy
import io.kloudformation.resource.s3.bucket
import io.kloudformation.resource.s3.bucketPolicy


class BucketProps(var indexDocument: String = "index.html", var errorDocument: String = indexDocument): Props
class PolicyProps(var bucketRef: Value<String>, var policyDocument: PolicyDocument): Props

class S3Website {
    class Builder {

        val s3Bucket = object : Modification<Bucket.Builder, BucketProps>() { }
        val s3BucketPolicy = object : Modification<BucketPolicy.Builder, PolicyProps>() {}

        fun KloudFormation.create() {
            val bucket = kotlin.with(s3Bucket) {
                val props = props(BucketProps())

                bucket {
                    accessControl(+"PublicRead")
                    websiteConfiguration {
                        indexDocument(props.indexDocument)
                        errorDocument(props.errorDocument)
                    }
                    modifyBuilder(props)
                }
            }
            with(s3BucketPolicy) {
                if (!remove) {
                    val props = props(PolicyProps(bucket.ref(), policyDocument {
                        statement(
                                action = io.kloudformation.model.iam.action("s3:GetObject"),
                                resource = io.kloudformation.model.iam.Resource(listOf(+"arn:aws:s3:::" + bucket.ref() + "/*"))
                        ) { allPrincipals() }
                    }))
                    bucketPolicy(
                            bucket = props.bucketRef,
                            policyDocument = props.policyDocument
                    ) {
                        modifyBuilder(props)
                    }
                }
            }
        }
    }
}
fun KloudFormation.s3Website(builder: S3Website.Builder.() -> Unit){
    with(S3Website.Builder()) {
        builder()
        create()
    }
}
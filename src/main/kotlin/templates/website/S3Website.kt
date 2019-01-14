package templates.website

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.iam.PolicyDocument
import io.kloudformation.model.iam.Resource
import io.kloudformation.model.iam.action
import io.kloudformation.model.iam.policyDocument
import io.kloudformation.module.*
import io.kloudformation.resource.aws.s3.Bucket
import io.kloudformation.resource.aws.s3.BucketPolicy
import io.kloudformation.resource.aws.s3.bucket
import io.kloudformation.resource.aws.s3.bucketPolicy


data class S3Website(val bucket: Bucket, val policy: BucketPolicy? = null, val distribution: S3Distribution? = null): Module {

    class Parts{
        class BucketProps(var indexDocument: String = "index.html", var errorDocument: String = indexDocument): Properties
        class PolicyProps(var bucketRef: Value<String>, var policyDocument: PolicyDocument): Properties

        fun s3Distribution(
               domain: Value<String>,
               httpMethod: HttpMethod = HttpMethod.HTTP2,
               sslSupportMethod: SslSupportMethod = SslSupportMethod.SNI,
               priceClass: CloudfrontPriceClass = CloudfrontPriceClass._200,
               modifications: Modification<S3Distribution.Parts,S3Distribution,S3Distribution.Predefined>.() -> Unit = {}
        ){
            s3Distribution.invoke(S3Distribution.Props(domain, httpMethod, sslSupportMethod, priceClass), modifications)
        }
        val s3Bucket = modification<Bucket.Builder, Bucket, BucketProps>()
        val s3BucketPolicy = optionalModification<BucketPolicy.Builder, BucketPolicy, PolicyProps>()
        val s3Distribution = SubModule({ pre: S3Distribution.Predefined, props: S3Distribution.Props -> S3Distribution.Builder(pre, props)})
    }

    class Builder: ModuleBuilder<S3Website, S3Website.Parts>(Parts()) {

        override fun KloudFormation.buildModule(): Parts.() -> S3Website = {
            val bucket = s3Bucket(Parts.BucketProps()) { props ->
                bucket {
                    accessControl(+"PublicRead")
                    websiteConfiguration {
                        indexDocument(props.indexDocument)
                        errorDocument(props.errorDocument)
                    }
                    modifyBuilder(props)
                }
            }
            val policyProps = Parts.PolicyProps(bucket.ref(), policyDocument {
                statement(
                        action = action("s3:GetObject"),
                        resource = Resource(listOf(+"arn:aws:s3:::" + bucket.ref() + "/*"))
                ) { allPrincipals() }
            })
            val policy = s3BucketPolicy(policyProps) { props ->
                bucketPolicy(
                        bucket = props.bucketRef,
                        policyDocument = props.policyDocument
                ) {
                    modifyBuilder(props)
                }
            }
            val distribution = s3Distribution.module(S3Distribution.Predefined(bucket.ref(), bucket.websiteConfiguration?.indexDocument ?: +"index.html"))()
            S3Website(bucket, policy, distribution)
        }
    }
}

val s3Website = builder(S3Website.Builder())
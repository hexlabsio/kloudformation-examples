package templates.website

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.iam.PolicyDocument
import io.kloudformation.model.iam.Resource
import io.kloudformation.model.iam.action
import io.kloudformation.model.iam.policyDocument
import io.kloudformation.resource.aws.s3.Bucket
import io.kloudformation.resource.aws.s3.BucketPolicy
import io.kloudformation.resource.aws.s3.bucket
import io.kloudformation.resource.aws.s3.bucketPolicy
import templates.*

data class S3Website(val bucket: Bucket, val policy: BucketPolicy?): Module, ExtraParts {

    class Parts(
        val s3Bucket: Modification<Bucket.Builder, Bucket, BucketProps> = modification(),
        val s3BucketPolicy: OptionalModification<BucketPolicy.Builder, BucketPolicy, PolicyProps> = optionalModification()
    ){
        class BucketProps(var indexDocument: String = "index.html", var errorDocument: String = indexDocument): Props
        class PolicyProps(var bucketRef: Value<String>, var policyDocument: PolicyDocument): Props

        class DistributionProps(val domain: Value<String>): PartialProps<S3Distribution.S3DistProps,S3Website>{
            override fun fullProps(extraProps: S3Website) = S3Distribution.S3DistProps(
                    domain = domain,
                    bucketRef = extraProps.bucket.ref(),
                    defaultRootObject = extraProps.bucket.websiteConfiguration?.indexDocument ?: Value.Of("index.html"),
                    httpMethod = HttpMethod.HTTP2,
                    sslSupportMethod = SslSupportMethod.SNI,
                    priceClass = CloudfrontPriceClass._200
            )
        }

        val s3Distribution = SubModule<S3Distribution, S3Distribution.Parts, S3Distribution.Builder, DistributionProps, S3Distribution.S3DistProps, S3Website>(
                builder = { props -> S3Distribution.Builder(props)}
        )

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
            val website = S3Website(bucket, policy)
            s3Distribution.module(website)()
            website
        }
    }
}

val s3Website = builder(S3Website.Builder())
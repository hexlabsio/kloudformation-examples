package templates

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsRegion
import io.kloudformation.model.iam.PolicyDocument
import io.kloudformation.model.iam.policyDocument
import io.kloudformation.property.aws.certificatemanager.certificate.DomainValidationOption
import io.kloudformation.property.aws.cloudfront.distribution.*
import io.kloudformation.resource.aws.certificatemanager.Certificate
import io.kloudformation.resource.aws.certificatemanager.certificate
import io.kloudformation.resource.aws.cloudfront.Distribution
import io.kloudformation.resource.aws.cloudfront.distribution
import io.kloudformation.resource.aws.s3.Bucket
import io.kloudformation.resource.aws.s3.BucketPolicy
import io.kloudformation.resource.aws.s3.bucket
import io.kloudformation.resource.aws.s3.bucketPolicy


class S3Website {
    class BucketProps(var indexDocument: String = "index.html", var errorDocument: String = indexDocument): Props
    class PolicyProps(var bucketRef: Value<String>, var policyDocument: PolicyDocument): Props
    class DomainModule {
        class DomainProps(var domain: Value<String>, var bucketRef: Value<String>, var defaultRootObject: Value<String>, var httpMethod: HttpMethod, var sslSupportMethod: SslSupportMethod, var priceClass: CloudfrontPriceClass): Props
        class DomainProps2(var bucketRef: Value<String>, var defaultRootObject: Value<String>, var httpMethod: HttpMethod, var sslSupportMethod: SslSupportMethod, var priceClass: CloudfrontPriceClass): Props
        class DomainModuleParts(var certificate: Certificate?, val distribution: Distribution?): Props
        enum class CertificationValidationMethod{ EMAIL, DNS }
        enum class SslSupportMethod(val value: String){ SNI("sni-only"), VIP("vip") }
        enum class HttpMethod(val value: String){ HTTP1_1("http1.1"), HTTP2("http2") }
        enum class CloudfrontPriceClass(val value: String){ _100("PriceClass_100"), _200("PriceClass_200"), ALL("PriceClass_ALL") }

        data class CertificateProps(var domain: Value<String>, var validationMethod: CertificationValidationMethod = CertificationValidationMethod.DNS): Props
        data class DistributionProps(var config: DistributionConfig): Props
        class Builder {
            val bucketCertificate = optionalModification<Certificate.Builder, Certificate, CertificateProps>()
            val cloudFrontDistribution = optionalModification<Distribution.Builder, Distribution, DistributionProps>()
            fun KloudFormation.create(domainProps: DomainProps): DomainModuleParts{
                val cert = bucketCertificate(CertificateProps(domainProps.domain)) { props ->
                    certificate(props.domain) {
                        subjectAlternativeNames(listOf(props.domain))
                        domainValidationOptions(listOf(DomainValidationOption(
                                domainName = props.domain,
                                validationDomain = props.domain
                        )))
                        validationMethod(props.validationMethod.toString())
                        modifyBuilder(props)
                    }
                }
                val origin = Origin(
                        id = +"s3Origin",
                        domainName = domainProps.bucketRef + +".s3-website-" + awsRegion + +".amazonaws.com",
                        customOriginConfig = CustomOriginConfig(
                                originProtocolPolicy = +"http-only"
                        )
                )
                val distributionProps = DistributionProps(DistributionConfig(
                        origins = listOf(origin),
                        enabled = +true,
                        aliases = +listOf(+"www." + domainProps.domain, domainProps.domain),
                        defaultCacheBehavior = DefaultCacheBehavior(
                                allowedMethods = +listOf(+"GET", +"HEAD", +"OPTIONS"),
                                forwardedValues = ForwardedValues(queryString = +true),
                                targetOriginId = origin.id,
                                viewerProtocolPolicy = +"allow-all"
                        ),
                        defaultRootObject = domainProps.defaultRootObject,
                        priceClass = +domainProps.priceClass.value,
                        httpVersion = +domainProps.httpMethod.value,
                        viewerCertificate = ViewerCertificate(acmCertificateArn = cert?.ref(), sslSupportMethod = +domainProps.sslSupportMethod.value)
                ))
                val cfDistribution = cloudFrontDistribution(distributionProps){ props ->
                    distribution(props.config) {

                        modifyBuilder(props)
                    }
                }
                return DomainModuleParts(cert, cfDistribution)
            }
        }
    }

    class Builder {

        val s3Bucket = modification<Bucket.Builder, Bucket, BucketProps>()
        val s3BucketPolicy = optionalModification<BucketPolicy.Builder, BucketPolicy, PolicyProps>()
        private var domainModification = modification<DomainModule.Builder, DomainModule.DomainModuleParts, DomainModule.DomainProps>()
        private var domainModule: ((DomainModule.DomainProps2)->DomainModule.DomainModuleParts)? = null

        fun KloudFormation.domain(domain: String, modification: Modification<DomainModule.Builder, DomainModule.DomainModuleParts, DomainModule.DomainProps>.()->Unit = {}){
            domainModule = { props: DomainModule.DomainProps2 ->
                domainModification.apply(modification)
                domainModification(DomainModule.DomainProps(+domain, props.bucketRef, props.defaultRootObject, props.httpMethod, props.sslSupportMethod, props.priceClass)) {
                    with(DomainModule.Builder()){
                        modifyBuilder(it)
                        create(it)
                    }
                }
            }

        }

        fun KloudFormation.create() {
            val bucket = s3Bucket(BucketProps()) { props ->
                bucket {
                    accessControl(+"PublicRead")
                    websiteConfiguration {
                        indexDocument(props.indexDocument)
                        errorDocument(props.errorDocument)
                    }
                    modifyBuilder(props)
                    this
                }
            }
            val policyProps = PolicyProps(bucket.ref(), policyDocument {
                statement(
                        action = io.kloudformation.model.iam.action("s3:GetObject"),
                        resource = io.kloudformation.model.iam.Resource(listOf(+"arn:aws:s3:::" + bucket.ref() + "/*"))
                ) { allPrincipals() }
            })
            s3BucketPolicy(policyProps) { props ->
                bucketPolicy(
                        bucket = props.bucketRef,
                        policyDocument = props.policyDocument
                ) {
                    modifyBuilder(props)
                    this
                }
            }
            if(domainModule != null){
                domainModule!!(DomainModule.DomainProps2(
                        bucket.ref(),
                        bucket.websiteConfiguration?.indexDocument ?: +"index.html",
                        DomainModule.HttpMethod.HTTP2,
                        DomainModule.SslSupportMethod.SNI,
                        DomainModule.CloudfrontPriceClass._200
                        ))
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
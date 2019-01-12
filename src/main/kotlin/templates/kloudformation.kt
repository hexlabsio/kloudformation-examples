package templates

import io.kloudformation.KloudFormation
import io.kloudformation.StackBuilder
import io.kloudformation.function.plus
import io.kloudformation.property.Tag
import templates.serverless.Serverless
import templates.serverless.serverless
import templates.website.S3Website
import templates.website.s3Website


class Stack: StackBuilder {
    fun KloudFormation.s3WebsiteExamples(){
        // S3 Website with default settings
        s3Website {  }

        // S3 Website with some modifications ( errorDocument and bucket name)
        s3Website {
            s3Bucket {
                props { errorDocument = "404.html" }
                modify {
                    bucketName("MyBucket")
                }
            }
            s3BucketPolicy {
                props { bucketRef = +"" }
            }

        }

        // S3 Website without default bucket policy
        s3Website {
            s3BucketPolicy { remove() }
        }

        // S3 Website with klouds.io domain attached to a cloudfront distribution
        s3Website {
            s3Distribution(S3Website.Parts.DistributionProps(+"klouds.io"))
        }

        val cert = "certFromSomewhereElse"
        // S3 Website with klouds.io domain attached to a cloudfront distribution with root object changed and tags added to distribution
        s3Website {
            s3Distribution(S3Website.Parts.DistributionProps(+"klouds.io")){
                props { this.defaultRootObject = +"another" + defaultRootObject }
                modify {
                    bucketCertificate { remove() }
                    cloudFrontDistribution {
                        props { config = config.copy(viewerCertificate = config.viewerCertificate?.copy(acmCertificateArn = +cert)) }
                        modify {
                            tags(listOf(Tag(+"A", +"B")))
                        }
                    }
                }
            }
        }

    }
    override fun KloudFormation.create() {
        //s3WebsiteExamples()
        serverless("myService") {
            globalRole {
                modify { path("/anotherPath/") }
            }
            serverlessFunction(Serverless.FuncProps(
                    codeLocationKey = parameter<String>("CodeLocationKey").ref(),
                    functionId = "myFunction",
                    handler = +"org.http4k.serverless.lambda.LambdaFunction::handle",
                    runtime = +"java8")
            )
            serverlessFunction(Serverless.FuncProps(
                    codeLocationKey = parameter<String>("CodeLocationKey2").ref(),
                    functionId = "myFunction2",
                    handler = +"org.http4k.serverless.lambda.LambdaFunction::handle",
                    runtime = +"java8")
            ){
                modify {
                    lambdaRole {
                        modify {
                            path("/SomewhereElse/")
                        }
                    }
                }
            }
        }
        // Run mvn package to produce template.yml (also checked in for reference)
    }
}
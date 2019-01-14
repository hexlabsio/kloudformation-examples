package templates

import io.kloudformation.KloudFormation
import io.kloudformation.StackBuilder
import io.kloudformation.module.value
import io.kloudformation.property.Tag
import templates.serverless.HttpModule
import templates.serverless.HttpPathModule
import templates.serverless.ServerlessFunction
import templates.serverless.serverless
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
            s3Distribution(+"klouds.io")
        }

        val cert = "certFromSomewhereElse"
        // S3 Website with klouds.io domain attached to a cloudfront distribution with root object changed and tags added to distribution
        s3Website {
            s3Distribution(+"klouds.io"){
                props {
                    this.rootObject = +("another" + this.rootObject.value())
                }
                modify {
                    cloudFrontDistribution {
                        modify { tags(listOf(Tag(+"A", +"B"))) }
                    }
                }
            }
        }

    }
    override fun KloudFormation.create() {
        s3WebsiteExamples()
        serverless("myService") {
            //Lambda that uses modified role, updates properties and runs in private subnets triggered by a get request
            serverlessFunction(ServerlessFunction.Props(
                    codeLocationKey = parameter<String>("CodeLocationKey2").ref(),
                    functionId = "myFunction2",
                    handler = +"org.http4k.serverless.lambda.LambdaFunction::handle",
                    runtime = +"java8")
            ){
                modify {
                    http(HttpModule.Props()){
                        modify {
                            path(HttpPathModule.Props(+"/myPath"))
                        }
                    }
                    lambdaFunction {
                        modify {
                            timeout(20)
                            environment{
                                variables(mapOf(
                                        "HTTP4K_BOOTSTRAP_CLASS" to +"handler"
                                ))
                            }
                        }
                    }
                }
            }
        }
        // Run mvn package to produce template.yml (also checked in for reference)
    }
}
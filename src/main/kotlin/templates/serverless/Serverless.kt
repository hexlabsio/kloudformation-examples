package templates.serverless

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsAccountId
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsPartition
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsRegion
import io.kloudformation.model.iam.*
import io.kloudformation.property.aws.iam.role.policy
import io.kloudformation.resource.aws.iam.Role
import io.kloudformation.resource.aws.iam.role
import io.kloudformation.resource.aws.s3.Bucket
import io.kloudformation.resource.aws.s3.bucket
import templates.*

class Serverless(val serviceName: String, val stage: String, val privateConfig: PrivateConfig?, val bucket: Bucket, val lambdaRole: Role?): Module, ExtraParts {

    class Globals(val serviceName: String, val stage: String): ExtraParts

    open class PrivateConfig(val securityGroups: Value<List<Value<String>>>? = null, val subnetIds: Value<List<Value<String>>>? = null)
    object NoPrivateConfig: PrivateConfig()

    class FuncProps(val functionId: String,
                    val codeLocationKey: Value<String>,
                    val handler: Value<String>,
                    val runtime: Value<String>,
                    val privateConfig: PrivateConfig? = null
    ): PartialProps<ServerlessFunction.FuncProps, Serverless> {
        override fun fullProps(extraProps: Serverless): ServerlessFunction.FuncProps {
            val private = if(privateConfig is NoPrivateConfig) null else privateConfig ?: extraProps.privateConfig
            return ServerlessFunction.FuncProps(functionId,extraProps.serviceName,extraProps.stage,codeLocationKey,handler,runtime,extraProps.bucket,extraProps.lambdaRole, private)
        }
    }

    class Parts(
            val deploymentBucket: Modification<Bucket.Builder, Bucket, BucketProps> = modification(),
            val globalRole: OptionalModification<Role.Builder, Role, RoleProps> = optionalModification(absent = true)
    ){
        val serverlessFunction = SubModules<ServerlessFunction, ServerlessFunction.Parts, ServerlessFunction.Builder,FuncProps, ServerlessFunction.FuncProps, Serverless>(
                builder = { props -> ServerlessFunction.Builder(props)}
        )
        class BucketProps: Props
        data class RoleProps(var assumedRolePolicyDocument: PolicyDocument): Props
    }

    class Builder(
            val serviceName: String,
            val stage: String,
            val privateConfig: PrivateConfig? = null
    ): ModuleBuilder<Serverless, Parts>(Parts()){

        override fun KloudFormation.buildModule(): Parts.() -> Serverless = {
            val bucketResource = deploymentBucket(Serverless.Parts.BucketProps()){ props ->
                bucket {
                    modifyBuilder(props)
                }
            }
            val roleResource = roleFor(serviceName, stage, globalRole)
            val serverless = Serverless(serviceName, stage, privateConfig, bucketResource,roleResource)
            serverlessFunction.modules().forEach{
                it.module(serverless)()
            }
            serverless
        }

        companion object {
            fun KloudFormation.roleFor(serviceName: String, stage: String, roleMod: OptionalModification<Role.Builder, Role, Parts.RoleProps>): Role?{
                val defaultAssumeRole = policyDocument(version = IamPolicyVersion.V2.version){
                    statement(action = action("sts:AssumeRole")){
                        principal(PrincipalType.SERVICE, listOf(+"lambda.amazonaws.com"))
                    }
                }
                val logResource = +"arn:" + awsPartition + ":logs:" + awsRegion + ":" + awsAccountId + ":log-group:/aws/lambda/$serviceName-$stage-definition:*"
                return roleMod(Serverless.Parts.RoleProps(defaultAssumeRole)){ props ->
                    role(props.assumedRolePolicyDocument){
                        policies(listOf(
                                policy(
                                        policyName = +"$stage-$serviceName-lambda",
                                        policyDocument = PolicyDocument(
                                                version = IamPolicyVersion.V2.version,
                                                statement = listOf(
                                                        PolicyStatement(
                                                                action = action("logs:CreateLogStream"),
                                                                resource = resource(logResource)
                                                        ),
                                                        PolicyStatement(
                                                                action = action("logs:PutLogEvents"),
                                                                resource = resource(logResource + ":*")
                                                        )
                                                )
                                        )
                                )
                        ))
                        path("/")
                        roleName(+"$serviceName-$stage-" + awsRegion + "-lambdaRole")
                        managedPolicyArns(listOf(
                                +"arn:" + awsPartition + ":iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
                        ))
                        modifyBuilder(props)
                    }
                }
            }
        }
    }
}

fun KloudFormation.serverless(
        serviceName: String,
        stage: String = "dev",
        privateConfig: Serverless.PrivateConfig? = null,
        partBuilder: Serverless.Parts.()->Unit = {}
) = builder(Serverless.Builder(serviceName, stage, privateConfig), partBuilder)

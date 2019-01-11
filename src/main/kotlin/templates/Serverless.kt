package templates

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsAccountId
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsPartition
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsRegion
import io.kloudformation.model.iam.*
import io.kloudformation.property.aws.iam.role.policy
import io.kloudformation.property.aws.lambda.function.Code
import io.kloudformation.resource.aws.iam.Role
import io.kloudformation.resource.aws.iam.role
import io.kloudformation.resource.aws.lambda.function
import io.kloudformation.resource.aws.logs.LogGroup
import io.kloudformation.resource.aws.logs.logGroup
import io.kloudformation.resource.aws.s3.Bucket
import io.kloudformation.resource.aws.s3.bucket
import io.kloudformation.resource.aws.lambda.Function as Lambda

class ServerlessFunction{

    class FuncProps(
            val functionId: String,
            val serviceName: String,
            val stage: String,
            val codeLocationKey: Value<String>,
            val handler: Value<String>,
            val runtime: Value<String>,
            val globalBucket: Bucket,
            val globalRole: Role? = null
    ): Props
    class Parts(
            val lambdaLogGroup: Modification<LogGroup.Builder, LogGroup, LogGroupProps> = modification(),
            val lambdaRole: OptionalModification<Role.Builder, Role, Serverless.Parts.RoleProps> = optionalModification(absent = true),
            val lambdaFunction: Modification<Lambda.Builder, Lambda, LambdaProps> = modification()
    ){
        data class LogGroupProps(var name: Value<String>): Props
        data class LambdaProps(var code: Code, var handler: Value<String>, var role: Value<String>, var runtime: Value<String>): Props
    }
    class Builder(
            val functionId: String,
            val serviceName: String,
            val stage: String,
            val codeLocationKey: Value<String>,
            val handler: Value<String>,
            val runtime: Value<String>,
            val globalBucket: Bucket,
            val globalRole: Role? = null
    ): ModuleBuilder<ServerlessFunction, Parts>(Parts()){
        override fun KloudFormation.buildModule(): Parts.() -> ServerlessFunction = {
            val logGroupResource = lambdaLogGroup(Parts.LogGroupProps(name = +"/aws/lambda/$serviceName-$stage-$functionId")) { props ->
                logGroup {
                    logGroupName(props.name)
                    modifyBuilder(props)
                }
            }
            val code = Code(
                    s3Bucket = globalBucket.ref(),
                    s3Key = codeLocationKey
            )
            if(globalRole == null) lambdaRole.keep()
            val roleResource = Serverless.Builder.run { roleFor(serviceName, stage, lambdaRole) } ?: globalRole
            lambdaFunction(Parts.LambdaProps(code,handler,roleResource?.ref() ?: +"",runtime)) { props ->
                function(props.code, props.handler, props.role, props.runtime,
                        dependsOn = listOfNotNull(logGroupResource.logicalName, roleResource?.logicalName)){
                    modifyBuilder(props)
                }
            }

            ServerlessFunction()
        }
    }
}

class Serverless(val bucket: Bucket, val lambdaRole: Role?){

    class Parts(
            val deploymentBucket: Modification<Bucket.Builder, Bucket, BucketProps> = modification(),
            val globalRole: OptionalModification<Role.Builder, Role, RoleProps> = optionalModification(absent = true)
    ){
        var functionModification = modification<ServerlessFunction.Builder, ServerlessFunction, ServerlessFunction.FuncProps>()
        var functionModule: ((Bucket, Role?)-> ServerlessFunction)? = null

        fun KloudFormation.serverlessFunction( functionId: String,
                                               serviceName: String,
                                               stage: String = "dev",
                                               codeLocationKey: Value<String>,
                                               handler: Value<String>,
                                               runtime: Value<String>,
                                               modification: ServerlessFunction.Parts.()->Unit = {}){
            functionModule = { bucket, role ->
                functionModification(ServerlessFunction.FuncProps(functionId, serviceName, stage,codeLocationKey, handler, runtime,bucket, role)){ props ->
                    with(ServerlessFunction.Builder(props.functionId, props.serviceName, props.stage, props.codeLocationKey, props.handler, props.runtime, props.globalBucket, props.globalRole)){
                        this.parts.apply(modification)
                        modifyBuilder(props)
                        create { }
                    }
                }
            }

        }

        class BucketProps: Props
        data class RoleProps(var assumedRolePolicyDocument: PolicyDocument): Props

    }
    class Builder(
            val serviceName: String,
            val stage: String
    ): ModuleBuilder<Serverless, Parts>(Parts()){

        override fun KloudFormation.buildModule(): Parts.() -> Serverless = {
            val bucketResource = deploymentBucket(Serverless.Parts.BucketProps()){ props ->
                bucket {
                    modifyBuilder(props)
                }
            }
            val roleResource = roleFor(serviceName, stage, globalRole)
            if(functionModule != null){
                functionModule!!(bucketResource, roleResource)
            }
            Serverless(bucketResource,roleResource)
        }

        companion object {
            fun KloudFormation.roleFor(serviceName: String, stage: String, roleMod: OptionalModification<Role.Builder,Role, Parts.RoleProps>): Role?{
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
        partBuilder: Serverless.Parts.()->Unit = {}
) = builder(Serverless.Builder(serviceName, stage), partBuilder)

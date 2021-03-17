import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.utils.Serialization

import scala.util.Try

object Main extends App {

  final val GroupName = "test.test.com"
  final val GroupVersion = "v1alpha1"
  final val Kind = "TestApplication"
  final val KindList = s"${Kind}List"
  final val Singular = "testapplication"
  final val Plural = "testapplications"
  final val Short = "testapp"
  final val ApiVersion = GroupName + "/" + GroupVersion
  final val ResourceName = s"$Plural.$GroupName"
  final val Scope = "Namespaced"

  val Crd =
    new CustomResourceDefinitionBuilder()
      .withNewMetadata()
      .withName(ResourceName)
      .endMetadata()
      .withNewSpec()
      .withGroup(GroupName)
      .withNewNames()
      .withNewKind(Kind)
      .withListKind(KindList)
      .withSingular(Singular)
      .withPlural(Plural)
      .withShortNames(Short)
      .endNames()
      .withVersion(GroupVersion)
      .withScope("Namespaced")
      .withPreserveUnknownFields(true)
      .withNewSubresources()
      .withNewStatus()
      .endStatus()
      .endSubresources()
      .endSpec()
      .withNewStatus()
      .withStoredVersions(GroupVersion)
      .endStatus()
      .build()

  val customResourceDefinitionContext =
    new CustomResourceDefinitionContext.Builder()
      .withVersion(GroupVersion)
      .withKind(Kind)
      .withGroup(GroupName)
      .withPlural(Plural)
      .withScope(Scope)
      .build()

  Serialization.jsonMapper.registerModule(DefaultScalaModule)

  val client = new DefaultKubernetesClient()

  client
    .apiextensions()
    .v1beta1()
    .customResourceDefinitions()
    .withName(ResourceName)
    .createOrReplace(Crd)

  Try { client
    .customResource(customResourceDefinitionContext)
    .inNamespace("default")
    .withName("mytestcr")
    .delete() }

  client
    .customResource(customResourceDefinitionContext)
    .inNamespace("default")
    .withName("mytestcr")
    .createOrReplace(s"""
                        | {
                        |   "kind": "${Kind}",
                        |   "apiVersion": "${ApiVersion}",
                        |   "metadata": {
                        |     "name": "mytestcr"
                        |   },
                        |   "key": "value1"
                        | }
                        |""".stripMargin)

  var found: java.util.Map[String, Object] = null
  while (Try {
    found = client
      .customResource(customResourceDefinitionContext)
      .inNamespace("default")
      .withName("mytestcr")
      .get()
    assert { found != null }
    println(s"Found\n${found}")
    found
  }.toOption.isEmpty) {
    Thread.sleep(200)
  }

  println(found.get("key"))

  found.put("key", "value2")

  client.customResource(customResourceDefinitionContext)
    .inNamespace("default")
    .withName("mytestcr")
    .edit(found)

  while (Try {
    found = client
      .customResource(customResourceDefinitionContext)
      .inNamespace("default")
      .withName("mytestcr")
      .get()
    assert { found != null }
    println(s"Edit:\n${found}")
    found
  }.toOption.isEmpty) {
    Thread.sleep(200)
  }

  println(found.get("key"))

  assert{ found.get("key") == "value2" }

  System.exit(0)
}

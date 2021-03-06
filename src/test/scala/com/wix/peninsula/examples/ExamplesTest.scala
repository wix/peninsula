package com.wix.peninsula.examples

import com.wix.peninsula.{Json, JsonPathElement}
import com.wix.peninsula.domain.{Item, Person}
import com.wix.peninsula.exceptions.JsonPathDoesntExistException
import org.specs2.mutable.SpecificationWithJUnit

import scala.util.Success

class ExamplesTest extends SpecificationWithJUnit {

val json = Json.parse(
  """
    {
      "id": 1,
      "name": "John",
      "mobile": null,
      "location": {"city": "Vilnius", "country": "LT"},
      "customer": { "id": 1, "name": "John"},
      "items": [{"name": "tomatoes", "sale": true}, {"name": "snickers", "sale": false}]
    }
  """)

  "Extraction" in {
    json.extractString("location.city") mustEqual "Vilnius"
    json.extractStringTry("location.city") mustEqual Success("Vilnius")
    json.extractStringTry("location.postCode") must beFailedTry.withThrowable[JsonPathDoesntExistException]
    json.extract[Person]("customer") mustEqual Person(id = 1, name = "John")
    json.extract[Seq[Boolean]]("items.sale") mustEqual Seq(true, false)
    json.extract[Seq[Item]]("items") mustEqual Seq(Item(name = "tomatoes", sale = true), Item(name = "snickers", sale = false))
    json.extract[Item]("items[1]") mustEqual Item(name = "snickers", sale = false)
  }

  "Path" in {
    val location: Json = json("location")
    location.extractString("city") mustEqual "Vilnius"
    location.extractString("country") mustEqual "LT"

    val items: Json = json("items")
    items.extractString("[0].name") mustEqual "tomatoes"
    items.extractString("[1].name") mustEqual "snickers"
    items.extract[Seq[String]]("name") mustEqual Seq("tomatoes", "snickers")
  }

  "Inspections" in {
    json("location.city").exists mustEqual true
    json("location.postCode").exists mustEqual false
    json("items[1].name").exists mustEqual true

    json("items[1].name").contains("snickers") mustEqual true

    json("id").isNull mustEqual false
    json("mobile").isNull mustEqual true
  }

  "Basic Json transformation" in {
    import com.wix.peninsula._
    import com.wix.peninsula.CopyConfigFactory._

    val config = TransformationConfig()
      .add(copyFields("id", "slug"))
      .add(copyField("name" -> "title"))
      .add(copy("images"))

    val json = Json.parse("""
      {
         "id":1,
         "slug":"raw-metal",
         "name":"Raw Metal Gym",
         "images":{
            "top":"//images/top.jpg",
            "background":"//images/background.png"
         }
      }
    """)

    json.transform(config) must_== Json.parse("""
      {
        "id": 1,
        "slug": "raw-metal",
        "title": "Raw Metal Gym",
        "images": {
          "top": "//images/top.jpg",
          "background": "//images/background.png"
        }
      }
    """)
  }

  "A More Advanced Json Transformation" in {
    import com.wix.peninsula._
    import com.wix.peninsula.CopyConfigFactory._
    import com.wix.peninsula.JsonValidators.NonEmptyStringValidator
    import org.json4s.JsonAST.JString

    object HttpsAppender extends JsonMapper {
      override def map(json: Json): Json = Json(json.node match {
        case JString(url) => JString("https:" + url)
        case x => x
      })
    }

    val config = TransformationConfig()
      .add(copyField("id"))
      .add(mergeObject("texts"))
      .add(copyField("images.top" -> "media.pictures.headerBackground")
        .withValidators(NonEmptyStringValidator)
        .withMapper(HttpsAppender))

    val json = Json.parse(
        """
          {
             "id":1,
             "slug":"raw-metal",
             "name":"Raw Metal Gym",
             "texts": {
               "name": "Raw metal gym",
               "description": "The best gym in town. Come and visit us today!"
             },
             "images":{
                "top":"//images/top.jpg",
                "background":"//images/background.png"
             }
          }
        """)

    json.transform(config) must_== Json.parse(
      """
        {
        	"id" : 1,
        	"name" : "Raw metal gym",
        	"description" : "The best gym in town. Come and visit us today!",
        	  "media" : {
        	    "pictures" : {
        	      "headerBackground" : "https://images/top.jpg"
        	    }
        	  }
        }
      """)
  }


  "Basic Json Translation" in {
    import com.wix.peninsula._

    val json = Json.parse(
      """
        {
           "id":1,
           "slug":"raw-metal",
           "name":"Raw Metal Gym",
           "images":{
              "top":"//images/top.jpg",
              "background":"//images/background.png"
           }
        }
      """)

    val config = Json.parse(
      """
        {
           "name":"Metalinis Gymas",
           "images":{
              "background":"//images/translated-background.png"
           }
        }
      """)

    json.translate(config) must_== Json.parse(
      """
        {
          "id": 1,
          "slug": "raw-metal",
          "name":"Metalinis Gymas",
            "images": {
              "top": "//images/top.jpg",
              "background":"//images/translated-background.png"
            }
        }
      """)
  }

  """Custom Json Translation""" in  {
    import com.wix.peninsula._
    import com.wix.peninsula.CopyConfigFactory._

    val json = Json.parse(
      """
        {
           "id":1,
           "slug":"raw-metal",
           "name":"Raw Metal Gym",
           "images":{
              "top":"//images/top.jpg",
              "background":"//images/background.png"
           },
           "features": [
              { "id": 1, "description": "Convenient location" },
              { "id": 2, "description": "Lots of space" }
           ]
        }
      """)

    val translation = Json.parse(
      """
        {
           "title":"Metalinis Gymas",
           "media": {
              "backgroundImage":"//images/translated-background.png"
           },
           "features": [
            { "id": 2, "description": "space translated" },
            { "id": 1, "description": "location translated" }
           ]
        }
      """)

    val featureConfig = TransformationConfig().add(copyField("description"))

    val config = TransformationConfig()
      .add(copyField("title" -> "name"))
      .add(copyField("media.backgroundImage" -> "images.background"))
      .add(copyArrayOfObjects(fromTo = "features", config = featureConfig, idField = "id"))

    json.translate(translation, config) must_== Json.parse(
      """
        {
          "id": 1,
          "slug": "raw-metal",
          "name":"Metalinis Gymas",
          "images": {
            "top": "//images/top.jpg",
            "background":"//images/translated-background.png"
          },
          "features": [
            { "id": 1, "description": "location translated" },
            { "id": 2, "description": "space translated" }
          ]
        }
      """)
  }

  "Fields Filtering" in {
    import com.wix.peninsula.Json

    val json = Json.parse("""{"id": 1, "name": "John", "office": "Wix Townhall", "role": "Engineer"}""")

    json.only(Set("id", "role")) must_== Json.parse(
      """
        {
          "id" : 1,
          "role" : "Engineer"
        }
      """
    )
  }

}

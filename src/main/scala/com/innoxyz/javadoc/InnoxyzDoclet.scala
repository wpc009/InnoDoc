package com.innoxyz.javadoc

import com.sun.javadoc._
import java.io._
import org.apache.velocity.app.{VelocityEngine, Velocity}
import org.apache.velocity.VelocityContext
import java.util
import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory
import java.util.Properties
import com.sun.tools.doclets.standard.Standard
import scala.collection.mutable.ArrayBuffer
import scala.beans.BeanProperty
import java.lang.reflect.Field
import java.lang.annotation.Annotation
import java.net.URLClassLoader

object InnoxyzDoclet extends Doclet {


    val vproperties = new Properties()
    val logger = LoggerFactory.getLogger(getClass)
    val emptyTagArray = new Array[Tag](0)
    val template = getClass.getClassLoader.getResource("templates/api.vm")
    val writer = System.out;
    val options: Options = new Options("",null)
    val buffer = new util.HashMap[String, util.ArrayList[String]]()
    val validDocOps = Map[String, OptionDescriber](
        "-rootpackage" -> new OptionDescriber(2, options.rootPackage_),
        "-ac" -> new OptionDescriber(2, options.ac_)
    )

    val dataModelCache = new util.HashMap[Class[_ <: Object],String]

    vproperties.load(getClass.getClassLoader.getResourceAsStream("velocity.properties"))
    Velocity.init(vproperties)

    def optionLength(option: String): Int = {
        if (validDocOps.contains(option)) {
            validDocOps(option).length
        } else {
            0
        }
    }

    def validOptions(ops: Array[Array[String]], errorReporter: DocErrorReporter): Boolean = {
        println("classpath:")
        println(getClass.getClassLoader.asInstanceOf[URLClassLoader].getURLs.mkString(";"))
        println("Print params:")
        val standardOps = new ArrayBuffer[Array[String]]()
        ops.foreach(paramParts => {
            val key = paramParts(0)
            if (!validDocOps.contains(key)) {
                standardOps += paramParts
            } else {
                try {
                    validDocOps(key).setter(paramParts(1))
                } catch {
                    case e: Throwable =>
//                        logger.error(e.getStackTrace.mkString("\n"))
                        e.printStackTrace()
                        logger.error(s"wrong parameter format : ${key} -> $paramParts(1)")
                        return false;
                }
            }
            var SEP = " -> "
            val line = paramParts.foldLeft(new StringBuilder) {
                (builder, part) => {
                    builder.append(part)
                    builder.append(SEP)
                    SEP = " "
                    builder
                }
            }.toString
            println("\t" + line)
        })
        Standard.validOptions(standardOps.toArray, errorReporter)
    }

    def main(args: Array[String]) {

        //        println(template)
        val ActionName = """[A-Z][a-z]+""".r
        var SEP = ""
        val result = ActionName.findAllMatchIn("TestPlayAction").foldLeft(new StringBuilder()) {
            (res, m) => {
                val phase = m.group(0).toLowerCase
                if (phase != "action") {
                    res.append(SEP)
                    SEP = "-"
                    res.append(m.group(0).toLowerCase)
                }
                res
            }
        }
        println(result)

    }

    def start(root: RootDoc): Boolean = {
        println(s"start parsing ")
        println("generate apidocs")
        val classes: Array[ClassDoc] = root.specifiedClasses()
        println(s"${root.name()} : ${classes.length}")

        //        classes.foreach {renderClass }
        root.specifiedPackages().foreach {
            p => {
                println(p.commentText());
                p.ordinaryClasses().foreach(parseClass)
            }
        }
        renderToSingleFile()
        true
    }

   /* def parseModelClass(doc: ClassDoc) {
        println(s"processing ${doc.qualifiedName()}")
        val fields = doc.fields()
        fields.foreach(f => {
        })
    }*/

    def parseClass(doc: ClassDoc) {
        println(s"processing ${doc.qualifiedName()}")
        val fields = doc.fields()
        val filedsMap = new util.HashMap[String, String]
        fields.foreach {
            f =>
            //                println(s"${f.name()}:${f.`type`()}")
                filedsMap.put(f.name(), f.`type`().simpleTypeName())
        }
        val apis = new util.ArrayList[APIElem]
        val namespaceMap = new util.HashMap[String,util.ArrayList[APIElem]]()
        val returnElems = new util.ArrayList[ReturnElem]()
        if (!doc.qualifiedName().startsWith(options.rootPackage)) {
            logger.error(s"wrong doc ${doc.qualifiedName()}")
            return
        }
        val namespace = if (options.rootPackage.length == doc.qualifiedName().lastIndexOf(".")) {
            "/"
        } else {
            logger.debug(s" doc class name -> ${doc.qualifiedName()}")
            val path = doc.qualifiedName()
                .substring(options
                .rootPackage
                .length,
                doc.qualifiedName().lastIndexOf(".")).replace(".", "/")
            s"$path/"
        }
        doc.methods().filter(mDoc => {
            val apiTag = mDoc.tags("@api")
            apiTag != null && apiTag.length > 0
        }) foreach {
            mDoc =>
                val api = mDoc.tags("@api")

                val actionName = if (mDoc.annotations().length > 0 && mDoc.annotations()(0).annotationType()
                    .simpleTypeName()
                    == "Action") {
                    var acName = ""
                    mDoc.annotations()(0).elementValues().foreach(value => {
                        val name = value.element().name();
                        if (name == "value") {
                            acName = value.value().value().toString
                        }
                    })
                    acName
                } else {
                    val ActionName = """[A-Z][a-z0-9]+""".r
                    var SEP = ""
                    val result = ActionName.findAllMatchIn(doc.simpleTypeName()).foldLeft(new StringBuilder()) {
                        (res, m) => {
                            val phase = m.group(0).toLowerCase
                            if (phase != "action") {
                                res.append(SEP)
                                SEP = "-"
                                res.append(m.group(0).toLowerCase)
                            }
                            res
                        }
                    }
                    result.toString()
                }
                val requires = Some(mDoc.tags("@required"))
                val optionals = Some(mDoc.tags("@optional"))
                val returns = Some(mDoc.tags("@return"))
                val params: util.List[ParamElem] = new util.ArrayList[ParamElem]

                val parseParam = (r: Tag, required: Boolean) => {
                    val text = r.text()
                    val ParamSeg = """([\w]+)[\s]+(.*)""".r
                    text match {
                        case ParamSeg(name, text) =>
                            val typeStr = filedsMap.get(name)
                            params += new ParamElem(name, typeStr, required, text)
                        case _ =>
                            logger.error("wrong param tag format!")

                    }
                }



                requires.getOrElse(emptyTagArray).foreach(parseParam(_, true))
                optionals.getOrElse(emptyTagArray).foreach(parseParam(_, false))
                val ReturnsFormat = """[\s]*([\w]+)[\s]*:[\s]*([^, ]+) ([^, ]+) ([^,}]+)(?:,)?""".r
                val TypeFormat = """@((?:[\w]+[.])*[\w]+)(?:\[@((?:[\w]+[.])*[\w]+)\])?""".r


                returns.getOrElse(emptyTagArray).foreach( returnTag => {
                    ReturnsFormat.findAllMatchIn(returnTag.text()).foreach{ r =>
                        val name = r.group(2)
                        val sample = name match {
                            case TypeFormat(name,paramType) =>
                                val clazz = Class.forName(name).asInstanceOf[Class[_ <: Object]]
                                dataModelToJSON(clazz)
                        }
                        returnElems += new ReturnElem(r.group(1),r.group(2),r.group(3).toBoolean,r.group(4),sample)
                    }
                })
                namespaceMap.getOrElse(namespace, {
                    val apilist = new util.ArrayList[APIElem]()
                    namespaceMap.put(namespace,apilist)
                    apilist
                }) += new APIElem(actionName, s"${namespace}${actionName}", api(0).text(), mDoc.commentText(), params)
        }
        val context = new VelocityContext

        context.put("path", s"${namespace}")
        context.put("apiMap", namespaceMap.entrySet())
        context.put("returns",returnElems)
        apis.foreach(api => println(s"${api.name}:${api.describe}"))
        val builder = new StringWriter()
        Velocity.mergeTemplate("templates/api.vm", "utf-8", context, builder)
        val array = buffer.getOrElse(namespace, {
            val array = new util.ArrayList[String]();
            buffer.put(namespace,
                array);
            array
        })
        array += builder.toString
        //        val outputFile = new File("docs\\" + doc.name() + ".html")
        //        val bufferedWriter = new BufferedWriter(new FileWriter(outputFile))
        //        bufferedWriter.write(builder.toString)
        //        bufferedWriter.flush()
        //        bufferedWriter.close()
    }

    def renderToSingleFile() {
        val context = new VelocityContext()
        val writer = new BufferedWriter(new FileWriter(s"docs/allInOne.html"))
        Velocity.mergeTemplate("templates/allInOne_header.vm", "utf-8", context, writer)
        buffer.foreach((entry) => {
            val namespace = entry._1
            val apis = entry._2
            context.put("namespace", namespace)
            context.put("apiHTMLs", apis)
            val template = Velocity.getTemplate("templates/namespace.vm")
            Velocity.mergeTemplate("templates/namespace.vm", "utf-8", context, writer)
        })
        Velocity.mergeTemplate("templates/allInOne_footer.vm", "utf-8", context, writer)
        writer.flush();
        writer.close();
    }

    def getAllFields(clazz:Class[_ <: Object],origin:List[Field]):List[Field] = {
        val fields = clazz.getDeclaredFields.toList
        val superClazz = clazz.getSuperclass.asInstanceOf[Class[_ <: Object]]
        if(superClazz == classOf[Object]){
            origin ++ fields
        }else{
            getAllFields(superClazz,fields ++ origin)
        }
    }
    def dataModelToJSON(clazz:Class[_ <: Object]):String = {
        val fields = getAllFields(clazz,Nil)
        val builder = new StringBuilder
        builder.append("{")
        fields.filter( f => !f.isAnnotationPresent(options.ac)).foreach( f =>
            builder.append(s""" ${f.getName} : ${f.getType.getSimpleName} ,""")
        )
        builder.append("}")
        builder.toString()
    }
}

class ReturnElem(val name:String,val tp:String,val isArray:Boolean,val describe:String,val example:String){
    def getName = name;
    def getTp = tp;
    def getIsArray = isArray;
    def getDescribe = describe;
    def getExample = example
}

class ParamElem(val name: String, val tp: String, val required: Boolean, val describe: String) {
    def getName = name

    def getTp = tp

    def getRequired = required

    def getDescribe = describe
}

class APIElem(val name: String, val fullPath: String, val describe: String,
              val block: String, val params: util.List[ParamElem]) {
    def getName = name

    def getDescribe = describe

    def getParams = params

    def getFullPath = fullPath

    def getBlock = block
}

class OptionDescriber(val length: Int, val setter: (String) => Unit)

class Options(var rootPackage: String, var ac: Class[_ <: Annotation]) {
    def rootPackage_ = (pack: String) => {
        rootPackage = pack
    }

    def ac_ = (acName: String) => {
        ac = Class.forName(acName).asInstanceOf[Class[_ <: Annotation]]
    }

}
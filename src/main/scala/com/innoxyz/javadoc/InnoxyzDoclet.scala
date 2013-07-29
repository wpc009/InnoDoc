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
import java.net.{URL, URLClassLoader}
import java.nio.file.{StandardCopyOption, Files, Paths, Path}

object InnoxyzDoclet extends Doclet {


    val vproperties = new Properties()
    val logger = LoggerFactory.getLogger(getClass)
    val emptyTagArray = new Array[Tag](0)
//    val template = getClass.getClassLoader.getResource("templates/api.vm")
    val writer = System.out;
    val options: Options = new Options("",null)
    val buffer = new util.HashMap[String, util.ArrayList[String]]()

    val apiCache = new util.HashMap[String,util.ArrayList[String]]()
    val validDocOps = Map[String, OptionDescriber](
        "-rootpackage" -> new OptionDescriber(2, options.rootPackage_),
        "-ac" -> new OptionDescriber(2, options.ac_),
        "-out" -> new OptionDescriber(2,options.out_),
        "-encoding" -> new OptionDescriber(2,options.encoding_),
        "-classpath" -> new OptionDescriber(2,options.classpath_)

    )

    val dataModelCache = new util.HashMap[Class[_ <: Object],String]


    def isPrimitiveClass(c:Class[_ <:Object]):Boolean = {
        if(classOf[Number].isAssignableFrom(c)||classOf[String].isAssignableFrom(c)||classOf[Boolean].isAssignableFrom(c))true
        else false
    }
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
                        e.printStackTrace(System.err)
                        logger.error(e.getMessage)
                        logger.error(s"wrong parameter format :${key} -> ${paramParts(1)}")
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
        copyResources()
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
        val filedsMap = new util.HashMap[String, String]
        var classDoc = doc;
        while(classDoc != null){
            classDoc.fields().foreach {
                f =>
                //                println(s"${f.name()}:${f.`type`()}")
                    filedsMap.put(f.name(), f.`type`().simpleTypeName())
            }
            classDoc = classDoc.superclass()
        }

        val apis = new util.ArrayList[APIElem]
//        val namespaceMap = new util.HashMap[String,util.ArrayList[APIElem]]()
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
            val apiTag = mDoc.tags(APIElem.tagName)
            apiTag != null && apiTag.length > 0
        }) foreach {
            mDoc =>
                val api = mDoc.tags(APIElem.tagName)
                val resultElems = new util.ArrayList[ResultElem]()
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
                val requires = Some(mDoc.tags(ParamElem.requireName))
                val optionals = Some(mDoc.tags(ParamElem.optionName))
                val results = Some(mDoc.tags(ResultElem.tagName))
                val params: util.List[ParamElem] = new util.ArrayList[ParamElem]

                val parseParam = (r: Tag, required: Boolean, paramList:util.List[ParamElem]) => {
                    val text = r.text()
                    val ParamSeg = """([\w]+)(?:[\s]+)?(.*)""".r
                    text match {
                        case ParamSeg(name, comments) =>
                            val typeStr = filedsMap.get(name)
                            paramList += new ParamElem(name, typeStr, required, comments)
                        case _ =>
                            logger.error(s"wrong param tag format! at ${mDoc.position()}")

                    }
                }



                requires.getOrElse(emptyTagArray).foreach(parseParam(_, true,params))
                optionals.getOrElse(emptyTagArray).foreach(parseParam(_, false,params))
//                val ReturnsFormat = """[\s]*([\w]+)[\s]*:[\s]*([^, ]+) ([^, ]+) ([^,}]+)(?:,)?""".r
                val TypeFormat = """@((?:[\w]+[.])*[\w]+)""".r


                results.getOrElse(emptyTagArray).foreach( returnTag => {

                    val text = TypeFormat.replaceAllIn(returnTag.text(), m =>{
                        logger.debug(s"groups:${m.groupCount}")
                        try{
                            val className = m.group(1)
                            val clazz = options.loader.loadClass(className).asInstanceOf[Class[_ <: Object]]
                            if(isPrimitiveClass(clazz)){
                                clazz.getSimpleName
                            }else
                                dataModelToJSON(clazz)
                        }catch {
                            case e:IllegalArgumentException =>
                                logger.error(s"wrong type format at ${mDoc.position().toString}")
                                m.group(0)
                            case e:ClassNotFoundException =>
                                logger.error(s"Class ${m.group(1)} not in the classpath")
                                m.group(1)
                            case e:Exception =>
                                logger.error(s"""illegal "@xxx.xxx.xxx" class reference at:${mDoc.position()}""")
                                m.group(1)
                        }
                    })


                    resultElems += new ResultElem(text)
//                    ReturnsFormat.findAllMatchIn(returnTag.text()).foreach{ r =>
//                        val name = r.group(2)
//                        val sample = name match {
//                            case TypeFormat(name) =>
//                                val clazz = Class.forName(name).asInstanceOf[Class[_ <: Object]]
//                                dataModelToJSON(clazz)
//                        }
//                        resultElems += new ResultElem(r.group(1),r.group(2),r.group(3).toBoolean,r.group(4),sample)
//                    }
                })
                apis += new APIElem(actionName, s"${namespace}${actionName}", api(0).text(), mDoc.commentText(), params,resultElems)
                val apiList = apiCache.getOrElse(namespace,{val l=new util.ArrayList[String]();apiCache += namespace -> l ; l})
                apiList += actionName

        }
        if(apis.length>0){
            val context = new VelocityContext
            context.put("apis", apis)
            val builder = new StringWriter()
            Velocity.mergeTemplate("templates/api.vm", options.encoding, context, builder)
            val array = buffer.getOrElse(namespace, {
                val array = new util.ArrayList[String]();
                buffer.put(namespace,
                    array);
                array
            })
            array += builder.toString
        }
    }

    def copyResources() {
        val apiCssStream = getClass.getClassLoader.getResourceAsStream("css/api.css")
        val folder = options.outFolder.resolve("css")
        if(Files.notExists(folder))
            Files.createDirectories(folder)
        Files.copy(apiCssStream,folder.resolve("api.css"),StandardCopyOption.REPLACE_EXISTING)

      val jsStream = getClass.getClassLoader.getResourceAsStream("js/jquery.min.js")
      val jsFolder = options.outFolder.resolve("js")
      if(Files.notExists(jsFolder))
        Files.createDirectories(jsFolder)
      Files.copy(jsStream,jsFolder.resolve("jquery.min.js"),StandardCopyOption.REPLACE_EXISTING)
    }

    def renderToSingleFile() {
        val context = new VelocityContext()
        if(Files.notExists(options.outFolder))Files.createDirectories(options.outFolder)
        val file = options.outFolder.resolve("allInOne.html")
        val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file.toFile),options.encoding))
        context.put("apiAgenda",apiCache)
        Velocity.mergeTemplate("templates/allInOne_header.vm", options.encoding, context, writer)


        buffer.foreach((entry) => {
            val namespace = entry._1
            val apis = entry._2
            if(apis.length>0){
                context.put("namespace", namespace)
                context.put("apiHTMLs", apis)
                Velocity.mergeTemplate("templates/namespace.vm", options.encoding, context, writer)
            }
        })
        Velocity.mergeTemplate("templates/allInOne_footer.vm", options.encoding, context, writer)
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
        var SEP=""
        builder.append("{")
        fields.filter( f => !f.isAnnotationPresent(options.ac)).foreach{ f =>
            builder.append(SEP)
            SEP=","
            builder.append(s""" ${f.getName} : ${f.getType.getSimpleName}""")
        }
        builder.append("}")
        builder.toString()
    }
}

object ResultElem{
    val tagName = "@result"
}

class ResultElem(val text:String){
    def getText = text;
}

object ParamElem{
    val requireName="@required"
    val optionName= "@optional"
}
class ParamElem(val name: String, val tp: String, val required: Boolean, val describe: String) {
    def getName = name

    def getTp = tp

    def getRequired = required

    def getDescribe = describe
}

object APIElem{
    val tagName="@api"
}
class APIElem(val name: String, val fullPath: String, val describe: String,
              val block: String, val params: util.List[ParamElem],val results:util.List[ResultElem]) {
    def getName = name

    def getDescribe = describe

    def getParams = params

    def getFullPath = fullPath

    def getBlock = block

    def getResults = results
}

class OptionDescriber(val length: Int, val setter: (String) => Unit)

class Options(var rootPackage: String, var ac: Class[_ <: Annotation]) {
    var outFolder:Path=Paths.get("docs")
    var encoding : String ="utf-8"
    var classpath:Array[String] = Array()
    var loader :URLClassLoader = null
    def rootPackage_ = (pack: String) => {
        rootPackage = pack
    }

    def ac_ = (acName: String) => {
        val urls = classpath.map( url => new File(url).toURI.toURL)
        loader = new URLClassLoader(urls,classOf[Doclet].getClassLoader)
        ac = loader.loadClass(acName).asInstanceOf[Class[_ <: Annotation]]
    }

    def out_ = (name:String) => {
        outFolder = Paths.get(name)
    }

    def encoding_ = (e:String) =>{
        this.encoding=e
    }

    def classpath_ = (cp:String) =>{
        this.classpath = cp.split(File.pathSeparator)
    }

}
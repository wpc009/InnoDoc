package com.innoxyz.javadoc.tags

import java.util.Map
import com.sun.tools.doclets.Taglet
/**
 * Created with IntelliJ IDEA.
 * User: wysa
 * Date: 13-4-16
 * Time: 下午6:36
 * To change this template use File | Settings | File Templates.
 */
class ActionTag extends BaseTaglet{
  def getName = ActionTag.NAME
}
object ActionTag{
  val NAME = "action"

  def register(tagletMap:Map[String,Taglet]):Unit = {
    tagletMap.put(NAME,new ActionTag)
  }

}

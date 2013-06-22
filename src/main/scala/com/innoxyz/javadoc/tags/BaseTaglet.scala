package com.innoxyz.javadoc.tags

import com.sun.tools.doclets.Taglet
import com.sun.javadoc.Tag

/**
 * Created with IntelliJ IDEA.
 * User: wysa
 * Date: 13-4-16
 * Time: 下午6:50
 * To change this template use File | Settings | File Templates.
 */
trait BaseTaglet extends Taglet{
  def isInlineTag = false
  def inField = false;
  def inConstructor = false;
  def inMethod = false;
  def inOverview = true;
  def inPackage = true;
  def inType = true

  def toString(tag:Tag):String={
    tag.text
  }
  def toString(tags:Array[Tag]):String = {
    val strBuilder = new StringBuilder
    tags.foreach( strBuilder.append(_))
    strBuilder.toString()
  }
}

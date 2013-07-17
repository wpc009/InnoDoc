package com.innoxyz.tests;

import javax.xml.ws.Action;

/**
 * Created by wysa on 13-6-21.
 */
public class TestDoc {

	private String name;
	private Integer id;

	/**
	 * comment blocks
	 * 2nd line
	 * 3rd line blablablablabla
	 *
	 *
	 * @api
	 * @required id blablablabla
	 * @optional name blablablablalbla
	 * @return {
	 *  data: @com.innoxyz.others.Data true 测试数据
	 * }
	 *
	 */
	@Action(input = "a",output="b")
	public void action(){

	}

	public static void main (String[] args) throws Exception{
		com.sun.tools.javadoc.Main.execute(args);
//		Class c = Class.forName("com.innoxyz.others.NotExpose");

	}
}

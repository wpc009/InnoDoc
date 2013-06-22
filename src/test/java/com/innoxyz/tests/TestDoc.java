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
	 * @api action designed to do something
	 * @required id blablablabla
	 * @optional name blablablablalbla
	 *
	 *
	 */
	@Action(input = "a",output="b")
	public void action(){

	}

	public static void main (String[] args){
		com.sun.tools.javadoc.Main.execute(args);

	}
}

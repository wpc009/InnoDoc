What is InnoDoc
======
An customized java-doc generator, used for generating API documentation. Writing the api documentation like a java doc. Designed for Strut2 Actions. Use `@Action` annotation to find actions.

Key words
======
* `@api` Indicate it's an API documentation. 
* `@required` fieldName [comments ... ] 
* `@optional` fieldName [comments ... ]
* `@result` result in json format. Explain what the result will be.
 
Usage
=======
I use `Maven` instead of `sbt`. Because it's more friendly to IDE.

Build the project.
<pre>mvn package</pre>

Put this in your pom.xml
```
    <build>
      <plugins>
        ....
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-javadoc-plugin</artifactId>
          <version>2.9.1</version>
          <configuration>
            <doclet>com.innoxyz.javadoc.InnoxyzDoclet</doclet>
            <docletArtifact>
              <groupId>com.innoxyz</groupId>
              <artifactId>innodoc</artifactId>
              <version>1.0-SNAPSHOT</version>
            </docletArtifact>
            <show>private</show>
            <additionalparam>-rootpackage {root_package_name} -ac {the_annotation_class_used_to_ignore_fields}</additionalparam>
            <useStandardDocletOptions>false</useStandardDocletOptions>
            <subpackages>com.innoxyz.actions</subpackages>
          </configuration>
        </plugin>
      </plugins>
    </build>
```

`rootpackage` is the root package of your actions.
`subpackages` is a standard javadoc parameter. See the help of javadoc

/*
* generated by Xtext
*/
package org.eclipse.xtext.example.tutorial.parser.antlr;

import java.io.InputStream;
import org.eclipse.xtext.parser.antlr.IAntlrTokenFileProvider;

public class TutorialAntlrTokenFileProvider implements IAntlrTokenFileProvider {
	
	@Override
	public InputStream getAntlrTokenFile() {
		ClassLoader classLoader = getClass().getClassLoader();
    	return classLoader.getResourceAsStream("org/eclipse/xtext/example/tutorial/parser/antlr/internal/InternalTutorial.tokens");
	}
}

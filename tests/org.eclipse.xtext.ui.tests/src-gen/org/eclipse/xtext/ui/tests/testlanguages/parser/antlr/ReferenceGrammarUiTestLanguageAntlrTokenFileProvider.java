/*
* generated by Xtext
*/
package org.eclipse.xtext.ui.tests.testlanguages.parser.antlr;

import java.io.InputStream;
import org.eclipse.xtext.parser.antlr.IAntlrTokenFileProvider;

public class ReferenceGrammarUiTestLanguageAntlrTokenFileProvider implements IAntlrTokenFileProvider {
	
	@Override
	public InputStream getAntlrTokenFile() {
		ClassLoader classLoader = getClass().getClassLoader();
    	return classLoader.getResourceAsStream("org/eclipse/xtext/ui/tests/testlanguages/parser/antlr/internal/InternalReferenceGrammarUiTestLanguage.tokens");
	}
}

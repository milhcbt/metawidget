// Metawidget
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package org.metawidget.statically.faces.component.html.widgetprocessor;

import static org.metawidget.inspector.InspectionResultConstants.*;

import java.io.StringWriter;
import java.util.Date;

import junit.framework.TestCase;

import org.metawidget.statically.faces.StaticFacesMetawidgetTests;
import org.metawidget.statically.faces.component.html.StaticHtmlMetawidget;
import org.metawidget.statically.faces.component.html.widgetbuilder.HtmlInputText;

/**
 * @author Richard Kennard
 */

public class CssStyleProcessorTest
	extends TestCase {

	//
	// Public methods
	//

	public void testWidgetProcessor()
		throws Exception {

		CssStyleProcessor processor = new CssStyleProcessor();
		HtmlInputText htmlInputText = new HtmlInputText();

		// No style

		StaticHtmlMetawidget metawidget = new StaticHtmlMetawidget();
		processor.processWidget( htmlInputText, PROPERTY, null, metawidget );
		StaticFacesMetawidgetTests.assertWidgetEquals( htmlInputText, "<h:inputText/>\r\n" );

		// Styles

		metawidget.setStyle( "foo" );
		metawidget.setStyleClass( "bar" );
		processor.processWidget( htmlInputText, PROPERTY, null, metawidget );
		StaticFacesMetawidgetTests.assertWidgetEquals( htmlInputText, "<h:inputText style=\"foo\" styleClass=\"bar\"/>\r\n" );
	}

	public void testSimpleType() {

		StaticHtmlMetawidget metawidget = new StaticHtmlMetawidget();
		metawidget.setStyle( "stylin" );
		metawidget.setStyleClass( "styleClassin" );
		metawidget.setValueExpression( "value", "#{foo}" );
		metawidget.setPath( Foo.class.getName() );

		StringWriter writer = new StringWriter();
		metawidget.write( writer );

		String result = "<h:panelGrid columns=\"2\">\r\n" +
				"\t<h:outputLabel for=\"fooBar\" value=\"Bar:\"/>\r\n" +
				"\t<h:inputText id=\"fooBar\" style=\"stylin\" styleClass=\"styleClassin\" value=\"#{foo.bar}\">\r\n" +
				"\t\t<f:convertDateTime/>\r\n" +
				"\t</h:inputText>\r\n" +
				"\t<h:outputLabel for=\"fooBaz\" value=\"Baz:\"/>\r\n" +
				"\t<h:inputText id=\"fooBaz\" style=\"stylin\" styleClass=\"styleClassin\" value=\"#{foo.baz}\"/>\r\n" +
				"</h:panelGrid>\r\n";

		assertEquals( result, writer.toString() );
	}

	//
	// Inner class
	//

	public static class Foo {

		public Date	bar;

		public int	baz;
	}
}

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

package org.metawidget.jsp.tagext;

import static org.metawidget.inspector.InspectionResultConstants.*;

import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.servlet.ServletContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyTagSupport;
import javax.servlet.jsp.tagext.Tag;

import org.metawidget.iface.MetawidgetException;
import org.metawidget.inspector.ConfigReader;
import org.metawidget.inspector.iface.Inspector;
import org.metawidget.inspector.jsp.JspAnnotationInspector;
import org.metawidget.jsp.ServletConfigReader;
import org.metawidget.layout.iface.Layout;
import org.metawidget.mixin.w3c.MetawidgetMixin;
import org.metawidget.util.ClassUtils;
import org.metawidget.util.CollectionUtils;
import org.metawidget.util.XmlUtils;
import org.metawidget.util.simple.PathUtils;
import org.metawidget.util.simple.StringUtils;
import org.metawidget.util.simple.PathUtils.TypeAndNames;
import org.metawidget.widgetbuilder.iface.WidgetBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Base Metawidget for JSP environments.
 *
 * @author Richard Kennard
 */

public abstract class MetawidgetTag
	extends BodyTagSupport
{
	//
	// Private statics
	//

	private final static long					serialVersionUID		= 1l;

	private final static String					CONFIG_READER_ATTRIBUTE	= "metawidget-config-reader";

	//
	// Private members
	//

	/**
	 * Path to inspect.
	 * <p>
	 * Set by subclasses according to what they prefer to call it (eg. <code>name</code> for Struts,
	 * <code>property</code> for Spring). Read by <code>WidgetBuilders</code>.
	 */

	private String								mPath;

	/**
	 * Prefix of path to inspect, to support nesting.
	 */

	private String								mPathPrefix;

	private String								mConfig					= "metawidget.xml";

	private boolean								mNeedsConfiguring		= true;

	private ResourceBundle						mBundle;

	private Map<String, String>					mParameters;

	private Map<String, FacetTag>				mFacets;

	private Map<String, StubTag>				mStubs;

	private Map<Object, Object>					mClientProperties;

	private MetawidgetMixin<Tag, MetawidgetTag>	mMetawidgetMixin;

	//
	// Constructor
	//

	public MetawidgetTag()
	{
		mMetawidgetMixin = newMetawidgetMixin();
	}

	//
	// Public methods
	//

	public String getPath()
	{
		return mPath;
	}

	public String getPathPrefix()
	{
		return mPathPrefix;
	}

	public void setConfig( String config )
	{
		mConfig = config;
		mNeedsConfiguring = true;
	}

	public String getLabelString( Map<String, String> attributes )
	{
		if ( attributes == null )
			return "";

		// Explicit label

		String label = attributes.get( LABEL );

		if ( label != null )
		{
			// (may be forced blank)

			if ( "".equals( label ) )
				return null;

			// (localize if possible)

			String localized = getLocalizedKey( StringUtils.camelCase( label ) );

			if ( localized != null )
				return localized.trim();

			return label.trim();
		}

		// Default name

		String name = attributes.get( NAME );

		if ( name != null )
		{
			// (localize if possible)

			String localized = getLocalizedKey( name );

			if ( localized != null )
				return localized.trim();

			return StringUtils.uncamelCase( name );
		}

		return "";
	}

	/**
	 * @return null if no bundle, ???key??? if bundle is missing a key
	 */

	public String getLocalizedKey( String key )
	{
		if ( mBundle == null )
			return null;

		try
		{
			String localizedKey = mBundle.getString( key );

			if ( localizedKey != null )
				return localizedKey;
		}
		catch ( MissingResourceException e )
		{
			// Fall through
		}

		return StringUtils.RESOURCE_KEY_NOT_FOUND_PREFIX + key + StringUtils.RESOURCE_KEY_NOT_FOUND_SUFFIX;
	}

	public void setParameter( String name, String value )
	{
		if ( mParameters == null )
			mParameters = CollectionUtils.newHashMap();

		mParameters.put( name, value );
	}

	public String getParameter( String name )
	{
		if ( mParameters == null )
			return null;

		return mParameters.get( name );
	}

	public FacetTag getFacet( String name )
	{
		if ( mFacets == null )
			return null;

		return mFacets.get( name );
	}

	public void setFacet( String name, FacetTag facetTag )
	{
		if ( mFacets == null )
			mFacets = CollectionUtils.newHashMap();

		mFacets.put( name, facetTag );
	}

	public void setStub( String path, StubTag stubTag )
	{
		if ( mStubs == null )
			mStubs = CollectionUtils.newHashMap();

		mStubs.put( path, stubTag );
	}

	public boolean isReadOnly()
	{
		return mMetawidgetMixin.isReadOnly();
	}

	public void setReadOnly( boolean readOnly )
	{
		mMetawidgetMixin.setReadOnly( readOnly );
	}

	public void setInspector( Inspector inspector )
	{
		mMetawidgetMixin.setInspector( inspector );
	}

	@SuppressWarnings( "unchecked" )
	public void setWidgetBuilder( WidgetBuilder<Object, ? extends MetawidgetTag> widgetBuilder )
	{
		mMetawidgetMixin.setWidgetBuilder( (WidgetBuilder) widgetBuilder );
	}

	/**
	 * Set the layout as a Class.
	 * <p>
	 * Generally Metawidgets provide a <code>setLayout</code> method, as opposed to
	 * <code>setLayoutClass</code>. However, instantiation of objects is cumbersome in JSPs, so this
	 * method is provided as a convenience. It instantiates the <code>Layout</code> given its
	 * fully-qualified class name.
	 */

	@SuppressWarnings( "unchecked" )
	public void setLayoutClass( String layoutClass )
	{
		// Layouts are immutable and threadsafe, so cache them at the application level

		ServletContext servletContext = this.getPageContext().getServletContext();
		Map<String, Layout<Tag, MetawidgetTag>> cachedLayouts = (Map<String, Layout<Tag, MetawidgetTag>>) servletContext.getAttribute( MetawidgetTag.class.getName() );

		if ( cachedLayouts == null )
		{
			cachedLayouts = CollectionUtils.newHashMap();
			servletContext.setAttribute( MetawidgetTag.class.getName(), cachedLayouts );
		}

		// Instantiate the Layout

		Layout<Tag, MetawidgetTag> layout = cachedLayouts.get( layoutClass );

		if ( layout == null )
		{
			try
			{
				layout = (Layout<Tag, MetawidgetTag>) Class.forName( layoutClass ).newInstance();
			}
			catch ( Exception e )
			{
				throw MetawidgetException.newException( e );
			}

			cachedLayouts.put( layoutClass, layout );
		}

		// Set the Layout

		setLayout( layout );
	}

	public void setLayout( Layout<Tag, MetawidgetTag> layout )
	{
		mMetawidgetMixin.setLayout( layout );
	}

	/**
	 * This method is public for use by WidgetBuilders.
	 */

	public PageContext getPageContext()
	{
		return pageContext;
	}

	/**
	 * This method is public for use by WidgetBuilders.
	 */

	public String inspect( Object toInspect, String type, String... names )
	{
		return mMetawidgetMixin.inspect( toInspect, type, names );
	}

	/**
	 * Storage area for WidgetProcessors, Layouts, and other stateless clients.
	 */

	public void putClientProperty( Object key, Object value )
	{
		if ( mClientProperties == null )
			mClientProperties = CollectionUtils.newHashMap();

		mClientProperties.put( key, value );
	}

	/**
	 * Storage area for WidgetProcessors, Layouts, and other stateless clients.
	 */

	@SuppressWarnings( "unchecked" )
	public <T> T getClientProperty( Object key )
	{
		if ( mClientProperties == null )
			return null;

		return (T) mClientProperties.get( key );
	}

	@Override
	public int doStartTag()
		throws JspException
	{
		// According to this bug report https://issues.apache.org/bugzilla/show_bug.cgi?id=16001 and
		// this article http://onjava.com/pub/a/onjava/2001/11/07/jsp12.html?page=3, we do not need
		// to worry about overriding super.release() for member variables associated with a property
		// getter/setter (nor can we ever rely on super.release() being called). We just need to
		// reset some internal variables during doStartTag

		mFacets = null;
		mStubs = null;

		return super.doStartTag();
	}

	@Override
	public int doEndTag()
		throws JspException
	{
		configure();

		try
		{
			mMetawidgetMixin.buildWidgets( inspect() );
		}
		catch ( Exception e )
		{
			throw MetawidgetException.newException( e );
		}

		// Reset parameters. We cannot do this in doStartTag because then it will
		// overwrite parameters set by metawidget.xml and/or by initNestedMetawidget

		mParameters = null;
		mNeedsConfiguring = true;

		return super.doEndTag();
	}

	//
	// Protected methods
	//

	/**
	 * Sets the path.
	 * <p>
	 * Set by subclasses according to what they prefer to call it (eg. <code>name</code> for Struts,
	 * <code>property</code> for Spring).
	 */

	protected void setPathInternal( String path )
	{
		mPath = path;

		// If changed the path, all bets are off what the prefix is

		mPathPrefix = null;
	}

	protected void setPathPrefix( String pathPrefix )
	{
		mPathPrefix = pathPrefix;
	}

	/**
	 * Sets the ResourceBundle used to localize labels.
	 * <p>
	 * This will need to be exposed in framework-specific ways. For example, JSTL can use
	 * <code>LocalizationContext</code>s, though these are not necessarily available to a Struts
	 * app.
	 */

	protected void setBundle( ResourceBundle bundle )
	{
		mBundle = bundle;
	}

	/**
	 * Instantiate the MetawidgetMixin used by this Metawidget.
	 * <p>
	 * Subclasses wishing to use their own MetawidgetMixin should override this method to
	 * instantiate their version.
	 */

	protected MetawidgetMixin<Tag, MetawidgetTag> newMetawidgetMixin()
	{
		return new MetawidgetTagMixin();
	}

	protected MetawidgetMixin<Tag, MetawidgetTag> getMetawidgetMixin()
	{
		return mMetawidgetMixin;
	}

	protected abstract void beforeBuildCompoundWidget( Element element );

	protected void initNestedMetawidget( MetawidgetTag nestedMetawidget, Map<String, String> attributes )
	{
		// Don't reconfigure...

		nestedMetawidget.setConfig( null );

		// ...instead, copy runtime values

		mMetawidgetMixin.initNestedMixin( nestedMetawidget.mMetawidgetMixin, attributes );
		nestedMetawidget.setPathInternal( mPath + StringUtils.SEPARATOR_DOT_CHAR + attributes.get( NAME ) );
		nestedMetawidget.setBundle( mBundle );

		if ( mParameters != null )
			nestedMetawidget.mParameters = CollectionUtils.newHashMap( mParameters );
	}

	protected String inspect()
	{
		TypeAndNames typeAndNames = PathUtils.parsePath( mPath, '.' );
		String type = typeAndNames.getType();

		// Inject the PageContext (in case it is used)

		try
		{
			JspAnnotationInspector.setThreadLocalPageContext( pageContext );
		}
		catch ( NoClassDefFoundError e )
		{
			// Fail gracefully (if running without JspAnnotationInspector installed)
		}
		catch ( UnsupportedClassVersionError e )
		{
			// Fail gracefully (if running without annotations)
		}

		// Inspect using the 'raw' type (eg. contactForm)

		String xml = inspect( null, type, typeAndNames.getNamesAsArray() );

		// (pageContext may be null in unit tests)

		if ( pageContext != null )
		{
			// Try to locate the runtime bean. This allows some Inspectors
			// to act on it polymorphically.

			Object obj = pageContext.findAttribute( type );

			if ( obj != null )
			{
				type = ClassUtils.getUnproxiedClass( obj.getClass() ).getName();
				String additionalXml = inspect( obj, type, typeAndNames.getNamesAsArray() );
				xml = combineSubtrees( xml, additionalXml );
			}
		}

		return xml;
	}

	protected void configure()
	{
		if ( !mNeedsConfiguring )
			return;

		mNeedsConfiguring = false;

		try
		{
			ServletContext servletContext = pageContext.getServletContext();
			ConfigReader configReader = (ConfigReader) servletContext.getAttribute( CONFIG_READER_ATTRIBUTE );

			if ( configReader == null )
			{
				configReader = new ServletConfigReader( servletContext );
				servletContext.setAttribute( CONFIG_READER_ATTRIBUTE, configReader );
			}

			if ( mConfig != null )
				configReader.configure( mConfig, this );

			mMetawidgetMixin.configureDefaults( configReader, getDefaultConfiguration(), MetawidgetTag.class );
		}
		catch ( Exception e )
		{
			throw MetawidgetException.newException( e );
		}
	}

	protected abstract String getDefaultConfiguration();

	protected StubTag getStub( String path )
	{
		if ( mStubs == null )
			return null;

		return mStubs.get( path );
	}

	//
	// Private methods
	//

	/**
	 * Combine the subtrees.
	 * <p>
	 * Note the top-level types attribute will be different, because one is the 'raw' type (eg.
	 * contactForm) and one the runtime bean (eg.
	 * org.metawidget.example.struts.addressbook.form.BusinessContactForm)
	 */

	public static String combineSubtrees( String master, String toAdd )
	{
		if ( master == null )
			return toAdd;

		if ( toAdd == null )
			return master;

		Document document = XmlUtils.documentFromString( master );
		Element masterElement = XmlUtils.getElementAt( document.getDocumentElement(), 0 );
		Element toAddElement = XmlUtils.getElementAt( XmlUtils.documentFromString( toAdd ).getDocumentElement(), 0 );
		XmlUtils.combineElements( masterElement, toAddElement, NAME, null );

		return XmlUtils.documentToString( document, false );
	}

	//
	// Inner class
	//

	protected class MetawidgetTagMixin
		extends MetawidgetMixin<Tag, MetawidgetTag>
	{
		//
		// Protected methods
		//

		@Override
		protected Tag getOverriddenWidget( String elementName, Map<String, String> attributes )
		{
			return MetawidgetTag.this.getStub( attributes.get( NAME ) );
		}

		@Override
		protected boolean isStub( Tag widget )
		{
			return ( widget instanceof StubTag );
		}

		@Override
		protected Map<String, String> getStubAttributes( Tag stub )
		{
			return ( (StubTag) stub ).getAttributesMap();
		}

		@Override
		protected void buildCompoundWidget( Element element )
			throws Exception
		{
			MetawidgetTag.this.beforeBuildCompoundWidget( element );
			super.buildCompoundWidget( element );
		}

		@Override
		protected MetawidgetTag buildNestedMetawidget( final Map<String, String> attributes )
			throws Exception
		{
			final MetawidgetTag metawidgetTag = MetawidgetTag.this.getClass().newInstance();
			MetawidgetTag.this.initNestedMetawidget( metawidgetTag, attributes );

			return metawidgetTag;
		}

		@Override
		protected MetawidgetTag getMixinOwner()
		{
			return MetawidgetTag.this;
		}

		@Override
		protected MetawidgetMixin<Tag, MetawidgetTag> getNestedMixin( MetawidgetTag metawidget )
		{
			return metawidget.getMetawidgetMixin();
		}
	}
}

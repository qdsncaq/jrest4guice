//$Id: FullTextQueryImpl.java 14199 2007-11-16 12:43:51Z epbernard $
package org.hibernate.search.query;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.annotations.common.util.ReflectHelper;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.engine.query.ParameterMetadata;
import org.hibernate.impl.AbstractQueryImpl;
import org.hibernate.impl.CriteriaImpl;
import org.hibernate.search.FullTextFilter;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.SearchException;
import org.hibernate.search.engine.DocumentBuilder;
import org.hibernate.search.engine.DocumentExtractor;
import org.hibernate.search.engine.EntityInfo;
import org.hibernate.search.engine.FilterDef;
import org.hibernate.search.engine.Loader;
import org.hibernate.search.engine.ObjectLoader;
import org.hibernate.search.engine.ProjectionLoader;
import org.hibernate.search.engine.QueryLoader;
import org.hibernate.search.engine.SearchFactoryImplementor;
import org.hibernate.search.filter.ChainedFilter;
import org.hibernate.search.filter.FilterKey;
import org.hibernate.search.store.DirectoryProvider;
import org.hibernate.search.util.ContextHelper;
import org.hibernate.transform.ResultTransformer;

/**
 * Implementation of {@link org.hibernate.search.FullTextQuery}
 *
 * @author Emmanuel Bernard
 * @author Hardy Ferentschik
 */
//TODO implements setParameter()
@SuppressWarnings("unchecked")
public class FullTextQueryImpl extends AbstractQueryImpl implements FullTextQuery {
	private static final Log log = LogFactory.getLog( FullTextQueryImpl.class );
	private org.apache.lucene.search.Query luceneQuery;
	private Class[] classes;
	private Set<Class> classesAndSubclasses;
	private Integer firstResult;
	private Integer maxResults;
	private Integer resultSize;
	private Sort sort;
	private Filter filter;
	private Criteria criteria;
	private String[] indexProjection;
	private ResultTransformer resultTransformer;
	private SearchFactoryImplementor searchFactoryImplementor;
	private Map<String, FullTextFilterImpl> filterDefinitions;
	private int fetchSize = 1;

	/**
	 * classes must be immutable
	 */
	public FullTextQueryImpl(org.apache.lucene.search.Query query, Class[] classes, SessionImplementor session,
							 ParameterMetadata parameterMetadata) {
		//TODO handle flushMode
		super( query.toString(), null, session, parameterMetadata );
		this.luceneQuery = query;
		this.classes = classes;
	}

	/**
	 * {@inheritDoc}
	 */
	public FullTextQuery setSort(Sort sort) {
		this.sort = sort;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public FullTextQuery setFilter(Filter filter) {
		this.filter = filter;
		return this;
	}

	/**
	 * Return an interator on the results.
	 * Retrieve the object one by one (initialize it during the next() operation)
	 */
	public Iterator iterate() throws HibernateException {
		//implement an interator which keep the id/class for each hit and get the object on demand
		//cause I can't keep the searcher and hence the hit opened. I dont have any hook to know when the
		//user stop using it
		//scrollable is better in this area

		SearchFactoryImplementor searchFactoryImplementor = ContextHelper.getSearchFactoryBySFI( session );
		//find the directories
		IndexSearcher searcher = buildSearcher( searchFactoryImplementor );
		if ( searcher == null ) {
			return new IteratorImpl( new ArrayList<EntityInfo>( 0 ), noLoader );
		}
		try {
			Hits hits = getHits( searcher );
			int first = first();
			int max = max( first, hits );
			Session sess = (Session) this.session;

			int size = max - first + 1 < 0 ? 0 : max - first + 1;
			List<EntityInfo> infos = new ArrayList<EntityInfo>( size );
			DocumentExtractor extractor = new DocumentExtractor( searchFactoryImplementor, indexProjection );
			for (int index = first; index <= max; index++) {
				//TODO use indexSearcher.getIndexReader().document( hits.id(index), FieldSelector(indexProjection) );
				infos.add( extractor.extract( hits, index ) );
			}
			Loader loader = getLoader( sess, searchFactoryImplementor );
			return new IteratorImpl( infos, loader );
		}
		catch (IOException e) {
			throw new HibernateException( "Unable to query Lucene index", e );
		}
		finally {
			try {
				searchFactoryImplementor.getReaderProvider().closeReader( searcher.getIndexReader() );
			}
			catch (SearchException e) {
				log.warn( "Unable to properly close searcher during lucene query: " + getQueryString(), e );
			}
		}
	}

	private Loader getLoader(Session session, SearchFactoryImplementor searchFactoryImplementor) {
		if ( indexProjection != null ) {
			ProjectionLoader loader = new ProjectionLoader();
			loader.init( session, searchFactoryImplementor, resultTransformer, indexProjection );
			return loader;
		}
		if ( criteria != null ) {
			if ( classes.length > 1 ) throw new SearchException( "Cannot mix criteria and multiple entity types" );
			if ( criteria instanceof CriteriaImpl ) {
				String targetEntity = ( (CriteriaImpl) criteria ).getEntityOrClassName();
				if ( classes.length == 1 && !classes[0].getName().equals( targetEntity ) ) {
					throw new SearchException( "Criteria query entity should match query entity" );
				}
				else {
					try {
						Class entityType = ReflectHelper.classForName( targetEntity );
						classes = new Class[] { entityType };
					}
					catch (ClassNotFoundException e) {
						throw new SearchException( "Unable to load entity class from criteria: " + targetEntity, e );
					}
				}
			}
			QueryLoader loader = new QueryLoader();
			loader.init( session, searchFactoryImplementor );
			loader.setEntityType( classes[0] );
			loader.setCriteria( criteria );
			return loader;
		}
		else if ( classes.length == 1 ) {
			QueryLoader loader = new QueryLoader();
			loader.init( session, searchFactoryImplementor );
			loader.setEntityType( classes[0] );
			return loader;
		}
		else {
			final ObjectLoader objectLoader = new ObjectLoader();
			objectLoader.init( session, searchFactoryImplementor );
			return objectLoader;
		}
	}

	public ScrollableResults scroll() throws HibernateException {
		//keep the searcher open until the resultset is closed
		SearchFactoryImplementor searchFactory = ContextHelper.getSearchFactoryBySFI( session );

		//find the directories
		IndexSearcher searcher = buildSearcher( searchFactory );
		//FIXME: handle null searcher
		Hits hits;
		try {
			hits = getHits( searcher );
			int first = first();
			int max = max( first, hits );
			DocumentExtractor extractor = new DocumentExtractor( searchFactory, indexProjection );
			Loader loader = getLoader( (Session) this.session, searchFactory );
			return new ScrollableResultsImpl( searcher, hits, first, max, fetchSize, extractor, loader, searchFactory );
		}
		catch (IOException e) {
			//close only in case of exception
			try {
				searchFactory.getReaderProvider().closeReader( searcher.getIndexReader() );
			}
			catch (SearchException ee) {
				//we have the initial issue already
			}
			throw new HibernateException( "Unable to query Lucene index", e );
		}
	}

	public ScrollableResults scroll(ScrollMode scrollMode) throws HibernateException {
		//TODO think about this scrollmode
		return scroll();
	}

	public List list() throws HibernateException {
		SearchFactoryImplementor searchFactoryImplementor = ContextHelper.getSearchFactoryBySFI( session );
		//find the directories
		IndexSearcher searcher = buildSearcher( searchFactoryImplementor );
		if ( searcher == null ) return new ArrayList( 0 );
		Hits hits;
		try {
			hits = getHits( searcher );
			int first = first();
			int max = max( first, hits );
			Session sess = (Session) this.session;

			int size = max - first + 1 < 0 ? 0 : max - first + 1;
			List<EntityInfo> infos = new ArrayList<EntityInfo>( size );
			DocumentExtractor extractor = new DocumentExtractor( searchFactoryImplementor, indexProjection );
			for (int index = first; index <= max; index++) {
				infos.add( extractor.extract( hits, index ) );
			}
			Loader loader = getLoader( sess, searchFactoryImplementor );
			List list = loader.load( infos.toArray( new EntityInfo[infos.size()] ) );
			if ( resultTransformer == null || loader instanceof ProjectionLoader) {
				//stay consistent with transformTuple which can only be executed during a projection
				return list;
			}
			else {
				return resultTransformer.transformList( list );
			}
		}
		catch (IOException e) {
			throw new HibernateException( "Unable to query Lucene index", e );
		}
		finally {
			try {
				searchFactoryImplementor.getReaderProvider().closeReader( searcher.getIndexReader() );
			}
			catch (SearchException e) {
				log.warn( "Unable to properly close searcher during lucene query: " + getQueryString(), e );
			}
		}
	}

	/**
	 * Execute the lucene search and return the machting hits.
	 *
	 * @param searcher The index searcher.
	 * @return The lucene hits.
	 * @throws IOException in case there is an error executing the lucene search.
	 */
	private Hits getHits(Searcher searcher) throws IOException {
		Hits hits;
		org.apache.lucene.search.Query query = filterQueryByClasses( luceneQuery );
		buildFilters();
		hits = searcher.search( query, filter, sort );
		setResultSize( hits );
		return hits;
	}

	private void buildFilters() {
		SearchFactoryImplementor searchFactoryImplementor = getSearchFactoryImplementor();
		if ( filterDefinitions != null && filterDefinitions.size() > 0 ) {
			ChainedFilter chainedFilter = new ChainedFilter();
			for ( FullTextFilterImpl filterDefinition : filterDefinitions.values() ) {
				FilterDef def = searchFactoryImplementor.getFilterDefinition( filterDefinition.getName() );
				Class implClass = def.getImpl();
				Object instance;
				try {
					instance = implClass.newInstance();
				}
				catch (InstantiationException e) {
					throw new SearchException( "Unable to create @FullTextFilterDef: " + def.getImpl(), e );
				}
				catch (IllegalAccessException e) {
					throw new SearchException( "Unable to create @FullTextFilterDef: " + def.getImpl(), e );
				}
				for ( Map.Entry<String, Object> entry : filterDefinition.getParameters().entrySet() ) {
					def.invoke( entry.getKey(), instance, entry.getValue() );
				}
				if ( def.isCache() && def.getKeyMethod() == null && filterDefinition.getParameters().size() > 0 ) {
					throw new SearchException("Filter with parameters and no @Key method: " + filterDefinition.getName() );
				}
				FilterKey key = null;
				if ( def.isCache() ) {
					if ( def.getKeyMethod() == null ) {
						key = new FilterKey( ) {
							public int hashCode() {
								return getImpl().hashCode();
							}

							public boolean equals(Object obj) {
								if ( ! ( obj instanceof FilterKey ) ) return false;
								FilterKey that = (FilterKey) obj;
								return this.getImpl().equals( that.getImpl() );
							}
						};
					}
					else {
						try {
							key = (FilterKey) def.getKeyMethod().invoke( instance );
						}
						catch (IllegalAccessException e) {
							throw new SearchException("Unable to access @Key method: "
									+ def.getImpl().getName() + "." + def.getKeyMethod().getName() );
						}
						catch (InvocationTargetException e) {
							throw new SearchException("Unable to access @Key method: "
									+ def.getImpl().getName() + "." + def.getKeyMethod().getName() );
						}
						catch (ClassCastException e) {
							throw new SearchException("@Key method does not return FilterKey: "
									+ def.getImpl().getName() + "." + def.getKeyMethod().getName() );
						}
					}
					key.setImpl( def.getImpl() );
				}

				Filter filter = def.isCache() ?
						searchFactoryImplementor.getFilterCachingStrategy().getCachedFilter( key ) :
						null;
				if (filter == null) {
					if ( def.getFactoryMethod() != null ) {
						try {
							filter = (Filter) def.getFactoryMethod().invoke( instance );
						}
						catch (IllegalAccessException e) {
							throw new SearchException("Unable to access @Factory method: "
									+ def.getImpl().getName() + "." + def.getFactoryMethod().getName() );
						}
						catch (InvocationTargetException e) {
							throw new SearchException("Unable to access @Factory method: "
									+ def.getImpl().getName() + "." + def.getFactoryMethod().getName() );
						}
						catch (ClassCastException e) {
							throw new SearchException("@Key method does not return a org.apache.lucene.search.Filter class: "
									+ def.getImpl().getName() + "." + def.getFactoryMethod().getName() );
						}
					}
					else {
						try {
							filter = (Filter) instance;
						}
						catch (ClassCastException e) {
							throw new SearchException("@Key method does not return a org.apache.lucene.search.Filter class: "
									+ def.getImpl().getName() + "." + def.getFactoryMethod().getName() );
						}
					}
					if ( def.isCache() ) searchFactoryImplementor.getFilterCachingStrategy().addCachedFilter( key, filter );
				}
				chainedFilter.addFilter( filter );
			}
			if ( filter != null ) chainedFilter.addFilter( filter );
			filter = chainedFilter;
		}
	}

	private org.apache.lucene.search.Query filterQueryByClasses(org.apache.lucene.search.Query luceneQuery) {
		//A query filter is more practical than a manual class filtering post query (esp on scrollable resultsets)
		//it also probably minimise the memory footprint
		if ( classesAndSubclasses == null ) {
			return luceneQuery;
		}
		else {
			BooleanQuery classFilter = new BooleanQuery();
			//annihilate the scoring impact of DocumentBuilder.CLASS_FIELDNAME
			classFilter.setBoost( 0 );
			for (Class clazz : classesAndSubclasses) {
				Term t = new Term( DocumentBuilder.CLASS_FIELDNAME, clazz.getName() );
				TermQuery termQuery = new TermQuery( t );
				classFilter.add( termQuery, BooleanClause.Occur.SHOULD );
			}
			BooleanQuery filteredQuery = new BooleanQuery();
			filteredQuery.add( luceneQuery, BooleanClause.Occur.MUST );
			filteredQuery.add( classFilter, BooleanClause.Occur.MUST );
			return filteredQuery;
		}
	}

	private int max(int first, Hits hits) {
		return maxResults == null ?
				hits.length() - 1 :
				maxResults + first < hits.length() ?
						first + maxResults - 1 :
						hits.length() - 1;
	}

	private int first() {
		return firstResult != null ?
				firstResult :
				0;
	}


	/**
	 * can return null
	 * TODO change classesAndSubclasses by side effect, which is a mismatch with the Searcher return, fix that.
	 */
	private IndexSearcher buildSearcher(SearchFactoryImplementor searchFactoryImplementor) {
		Map<Class, DocumentBuilder<Object>> builders = searchFactoryImplementor.getDocumentBuilders();
		List<DirectoryProvider> directories = new ArrayList<DirectoryProvider>();
		if ( classes == null || classes.length == 0 ) {
			//no class means all classes
			for (DocumentBuilder builder : builders.values()) {
				final DirectoryProvider[] directoryProviders =
						builder.getDirectoryProviderSelectionStrategy().getDirectoryProvidersForAllShards();
				for (DirectoryProvider provider : directoryProviders) {
					if ( !directories.contains( provider ) ) {
						directories.add( provider );
					}
				}
			}
			classesAndSubclasses = null;
		}
		else {
			Set<Class> involvedClasses = new HashSet<Class>( classes.length );
			Collections.addAll( involvedClasses, classes );
			for (Class clazz : classes) {
				DocumentBuilder builder = builders.get( clazz );
				if ( builder != null ) involvedClasses.addAll( builder.getMappedSubclasses() );
			}
			for (Class clazz : involvedClasses) {
				DocumentBuilder builder = builders.get( clazz );
				//TODO should we rather choose a polymorphic path and allow non mapped entities
				if ( builder == null )
					throw new HibernateException( "Not a mapped entity (don't forget to add @Indexed): " + clazz );

//				final DirectoryProvider[] directoryProviders = 
//						builder.getDirectoryProviderSelectionStrategy().getDirectoryProvidersForAllShards();
				
				//changed by jerry
				final DirectoryProvider[] directoryProviders = 
					builder.getDirectoryProviderSelectionStrategy().getDirectoryProvidersForSearch(clazz,luceneQuery);

				for (DirectoryProvider provider : directoryProviders) {
					if ( !directories.contains( provider ) ) {
						directories.add( provider );
					}
				}
			}
			classesAndSubclasses = involvedClasses;
		}

		//set up the searcher
		final DirectoryProvider[] directoryProviders = directories.toArray( new DirectoryProvider[directories.size()] );
		return new IndexSearcher( searchFactoryImplementor.getReaderProvider().openReader( directoryProviders ) );
	}

	private void setResultSize(Hits hits) {
		resultSize = hits.length();
	}


	public int getResultSize() {
		if ( resultSize == null ) {
			//get result size without object initialization
			SearchFactoryImplementor searchFactoryImplementor = ContextHelper.getSearchFactoryBySFI( session );
			IndexSearcher searcher = buildSearcher( searchFactoryImplementor );
			if ( searcher == null ) {
				resultSize = 0;
			}
			else {
				Hits hits;
				try {
					hits = getHits( searcher );
					resultSize = hits.length();
				}
				catch (IOException e) {
					throw new HibernateException( "Unable to query Lucene index", e );
				}
				finally {
					//searcher cannot be null
					try {
						searchFactoryImplementor.getReaderProvider().closeReader( searcher.getIndexReader() );
					}
					catch (SearchException e) {
						log.warn( "Unable to properly close searcher during lucene query: " + getQueryString(), e );
					}
				}
			}
		}
		return this.resultSize;
	}

	public FullTextQuery setCriteriaQuery(Criteria criteria) {
		this.criteria = criteria;
		return this;
	}

	public FullTextQuery setProjection(String... fields) {
		if ( fields == null || fields.length == 0 ) {
			this.indexProjection = null;
		}
		else {
			this.indexProjection = fields;
		}
		return this;
	}

	public FullTextQuery setFirstResult(int firstResult) {
		if (firstResult < 0) {
			throw new IllegalArgumentException("'first' pagination parameter less than 0");
		}
		this.firstResult = firstResult;
		return this;
	}

	public FullTextQuery setMaxResults(int maxResults) {
		if (maxResults < 0) {
			throw new IllegalArgumentException("'max' pagination parameter less than 0");
		}
		this.maxResults = maxResults;
		return this;
	}

	public FullTextQuery setFetchSize(int fetchSize) {
		super.setFetchSize( fetchSize );
		if ( fetchSize <= 0 ) {
			throw new IllegalArgumentException( "'fetch size' parameter less than or equals to 0" );
		}
		this.fetchSize = fetchSize;
		return this;
	}

	@Override
	public FullTextQuery setResultTransformer(ResultTransformer transformer) {
		super.setResultTransformer( transformer );
		this.resultTransformer = transformer;
		return this;
	}

	public int executeUpdate() throws HibernateException {
		throw new HibernateException( "Not supported operation" );
	}

	public Query setLockMode(String alias, LockMode lockMode) {
		return null;
	}

	protected Map getLockModes() {
		return null;
	}

	public FullTextFilter enableFullTextFilter(String name) {
		if ( filterDefinitions == null ) {
			filterDefinitions = new HashMap<String, FullTextFilterImpl>();
		}
		FullTextFilterImpl filterDefinition = filterDefinitions.get( name );
		if ( filterDefinition != null ) return filterDefinition;

		filterDefinition = new FullTextFilterImpl();
		filterDefinition.setName( name );
		FilterDef filterDef = getSearchFactoryImplementor().getFilterDefinition( name );
		if (filterDef == null) {
			throw new SearchException("Unkown @FullTextFilter: " + name);
		}
		filterDefinitions.put(name, filterDefinition);
		return filterDefinition;
	}

	public void disableFullTextFilter(String name) {
		filterDefinitions.remove( name );
	}

	private SearchFactoryImplementor getSearchFactoryImplementor() {
		if ( searchFactoryImplementor == null ) {
			searchFactoryImplementor = ContextHelper.getSearchFactoryBySFI( session );
		}
		return searchFactoryImplementor;
	}

	private static Loader noLoader = new Loader() {
		public void init(Session session, SearchFactoryImplementor searchFactoryImplementor) {
		}

		public Object load(EntityInfo entityInfo) {
			throw new UnsupportedOperationException( "noLoader should not be used" );
		}

		public List load(EntityInfo... entityInfos) {
			throw new UnsupportedOperationException( "noLoader should not be used" );
		}
	};
}

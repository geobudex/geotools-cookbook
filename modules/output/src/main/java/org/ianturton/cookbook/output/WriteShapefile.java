package org.ianturton.cookbook.output;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.NameImpl;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.geotools.feature.type.GeometryDescriptorImpl;
import org.geotools.feature.type.GeometryTypeImpl;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.filter.identity.FeatureId;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;

public class WriteShapefile {
	File outfile;
	private ShapefileDataStore shpDataStore;

	public WriteShapefile(File f) {
		outfile = f;

		ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

		Map<String, Serializable> params = new HashMap<String, Serializable>();
		try {
			params.put("url", outfile.toURI().toURL());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		params.put("create spatial index", Boolean.TRUE);

		try {
			shpDataStore = (ShapefileDataStore) dataStoreFactory
					.createNewDataStore(params);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public boolean writeFeatures(
			FeatureCollection<SimpleFeatureType, SimpleFeature> features) {

		if (shpDataStore == null) {
			throw new IllegalStateException("Datastore can not be null when writing");
		}
		SimpleFeatureType schema = features.getSchema();
		GeometryDescriptor geom = schema.getGeometryDescriptor();

		try {

			/*
			 * Write the features to the shapefile
			 */
			Transaction transaction = new DefaultTransaction("create");

			String typeName = shpDataStore.getTypeNames()[0];
			SimpleFeatureSource featureSource = shpDataStore
					.getFeatureSource(typeName);

			/*
			 * The Shapefile format has a couple limitations: - "the_geom" is always
			 * first, and used for the geometry attribute name - "the_geom" must be of
			 * type Point, MultiPoint, MuiltiLineString, MultiPolygon - Attribute
			 * names are limited in length - Not all data types are supported (example
			 * Timestamp represented as Date)
			 *
			 * Because of this we have to rename the geometry element and then rebuild
			 * the features to make sure that it is the first attribute.
			 */

			List<AttributeDescriptor> attributes = schema.getAttributeDescriptors();
			GeometryType geomType = null;
			List<AttributeDescriptor> attribs = new ArrayList<AttributeDescriptor>();
			for (AttributeDescriptor attrib : attributes) {
				AttributeType type = attrib.getType();
				if (type instanceof GeometryType) {
					geomType = (GeometryType) type;

				} else {
					attribs.add(attrib);
				}
			}

			GeometryTypeImpl gt = new GeometryTypeImpl(new NameImpl("the_geom"),
					geomType.getBinding(), geomType.getCoordinateReferenceSystem(),
					geomType.isIdentified(), geomType.isAbstract(),
					geomType.getRestrictions(), geomType.getSuper(),
					geomType.getDescription());

			GeometryDescriptor geomDesc = new GeometryDescriptorImpl(gt,
					new NameImpl("the_geom"), geom.getMinOccurs(), geom.getMaxOccurs(),
					geom.isNillable(), geom.getDefaultValue());

			attribs.add(0, geomDesc);

			SimpleFeatureType shpType = new SimpleFeatureTypeImpl(schema.getName(),
					attribs, geomDesc, schema.isAbstract(), schema.getRestrictions(),
					schema.getSuper(), schema.getDescription());

			shpDataStore.createSchema(shpType);

			if (featureSource instanceof SimpleFeatureStore) {
				SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

				List<SimpleFeature> feats = new ArrayList<SimpleFeature>();

				FeatureIterator<SimpleFeature> features2 = features.features();
				while (features2.hasNext()) {
					SimpleFeature f = features2.next();
					SimpleFeature reType = SimpleFeatureBuilder.build(shpType,
							f.getAttributes(), "");

					feats.add(reType);
				}
				features2.close();
				SimpleFeatureCollection collection = new ListFeatureCollection(shpType,
						feats);

				featureStore.setTransaction(transaction);
				try {
					List<FeatureId> ids = featureStore.addFeatures(collection);
					transaction.commit();
				} catch (Exception problem) {
					problem.printStackTrace();
					transaction.rollback();
				} finally {
					transaction.close();
				}
				shpDataStore.dispose();
				return true;
			} else {
				shpDataStore.dispose();
				System.err.println("ShapefileStore not writable");
				return false;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static void main(String[] args) {
		File file;
		if (args.length > 0) {
			file = new File(args[0]);
		} else {
			file = new File("ian.shp");
		}
		// create some random features and write them out;
		List<SimpleFeature> feats = new ArrayList<SimpleFeature>();
		SimpleFeatureType schema = null;
		try {
			schema = DataUtilities.createType("", "Location",
					"locations:Point:srid=4326," + // <- the geometry attribute:
							// Point type
							"name:String," + // <- a String attribute
							"number:Integer" // a number attribute
					);
		} catch (SchemaException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		for (int i = 0; i < 10; i++) {
			SimpleFeature f = createSimpleFeature(schema);
			feats.add(f);
		}
		WriteShapefile writer = new WriteShapefile(file);

		FeatureCollection<SimpleFeatureType, SimpleFeature> features = new ListFeatureCollection(
				schema, feats);
		writer.writeFeatures(features);
	}

	private static SimpleFeature createSimpleFeature(SimpleFeatureType schema) {
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(schema);
		double latitude = (Math.random() * 180.0) - 90.0;
		double longitude = (Math.random() * 360.0) - 180.0;
		String name = "thing" + Math.random();
		int number = (int) Math.round(Math.random() * 10.0);

		GeometryFactory geometryFactory = new GeometryFactory();
		/* Longitude (= x coord) first ! */
		com.vividsolutions.jts.geom.Point point = geometryFactory
				.createPoint(new Coordinate(longitude, latitude));

		featureBuilder.add(point);
		featureBuilder.add(name);
		featureBuilder.add(number);
		SimpleFeature feature = featureBuilder.buildFeature(null);
		return feature;
	}
}
package org.eclipse.update.internal.core;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved. 
 */
import java.io.*;
import java.net.URL;
import java.util.*;

import org.eclipse.core.boot.BootLoader;
import org.eclipse.core.boot.IPlatformConfiguration;
import org.eclipse.core.runtime.*;
import org.eclipse.update.configuration.*;
import org.eclipse.update.core.*;
import org.eclipse.update.core.model.*;
import org.eclipse.update.internal.model.ConfigurationActivityModel;
import org.eclipse.update.internal.model.InstallChangeParser;

/**
 * This class manages the reconciliation.
 */

public class SiteReconciler extends ModelObject {

	private SiteLocal siteLocal;
	private static final String SIMPLE_EXTENSION_ID = "deltaHandler";
	//$NON-NLS-1$
	private static final String INSTALL_DELTA_HANDLER =
		"org.eclipse.update.core.deltaHandler.display";
	//$NON-NLS-1$

	/**
	 * 
	 */
	public SiteReconciler(SiteLocal siteLocal) {
		this.siteLocal = siteLocal;
	}

	/*
	 * Reconciliation is the comparison between the old preserved state and the new one from platform.cfg
	 * 
	 * If the old state contained sites that are not in the new state, the old sites are not added to the state
	 * 
	 * If the new state contains sites that were not in the old state, configure the site and configure all the found features
	 * 
	 * If the sites are in both states, verify the features
	 * 
	 * if the old site contained features that are not in the new site, the features are not added to the site
	 * 
	 * if the new site contains feature that were not in the old site, configure the new feature
	 * 
	 * if the feature is in both site (old and new), use old feature state
	 * 
	 * When adding a feature to a site, we will check if the feature is broken or not. 
	 * A feature is broken when at least one of its plugin is not installed on the site.
	 * 
	 * At the end, go over all the site, get the configured features and make sure that if we find duplicates
	 * only one feature is configured
	 */
	public void reconcile() throws CoreException {

		IPlatformConfiguration platformConfig =
			BootLoader.getCurrentPlatformConfiguration();
		IPlatformConfiguration.ISiteEntry[] newSiteEntries =
			platformConfig.getConfiguredSites();
		IInstallConfiguration newDefaultConfiguration =
			siteLocal.cloneConfigurationSite(null, null, null);
		IConfiguredSite[] oldConfiguredSites = new IConfiguredSite[0];

		// sites from the current configuration
		if (siteLocal.getCurrentConfiguration() != null)
			oldConfiguredSites = siteLocal.getCurrentConfiguration().getConfiguredSites();

		// check if sites from the platform are new sites or modified sites
		// if they are new add them, if they are modified, compare them with the old
		// one and add them
		for (int siteIndex = 0; siteIndex < newSiteEntries.length; siteIndex++) {
			IPlatformConfiguration.ISiteEntry currentSiteEntry = newSiteEntries[siteIndex];
			URL resolvedURL = resolveSiteEntry(currentSiteEntry);
			boolean found = false;
			IConfiguredSite currentConfigurationSite = null;

			// check if SiteEntry has been possibly modified
			// if it was part of the previously known configuredSite
			for (int index = 0; index < oldConfiguredSites.length && !found; index++) {
				currentConfigurationSite = oldConfiguredSites[index];
				if (currentConfigurationSite.getSite().getURL().equals(resolvedURL)) {
					found = true;
					ConfiguredSite reconciledConfiguredSite = reconcile(currentConfigurationSite);
					reconciledConfiguredSite.setPreviousPluginPath(
						currentSiteEntry.getSitePolicy().getList());
					newDefaultConfiguration.addConfiguredSite(reconciledConfiguredSite);
				}
			}

			// old site not found, this is a new site, create it
			if (!found) {
				ISite site = SiteManager.getSite(resolvedURL);

				//site policy
				IPlatformConfiguration.ISitePolicy sitePolicy =
					currentSiteEntry.getSitePolicy();
				ConfiguredSite configSite =
					(ConfiguredSite) new BaseSiteLocalFactory().createConfigurationSiteModel(
						(SiteModel) site,
						sitePolicy.getType());
				configSite.setPlatformURLString(currentSiteEntry.getURL().toExternalForm());
				configSite.setPreviousPluginPath(currentSiteEntry.getSitePolicy().getList());

				//the site may not be read-write
				configSite.isUpdatable(newSiteEntries[siteIndex].isUpdateable());

				// Add the features as configured
				IFeatureReference[] newFeaturesRef = site.getFeatureReferences();
				for (int i = 0; i < newFeaturesRef.length; i++) {
					FeatureReferenceModel newFeatureRefModel =
						(FeatureReferenceModel) newFeaturesRef[i];
					configSite.getConfigurationPolicy().addConfiguredFeatureReference(
						newFeatureRefModel);
				}

				newDefaultConfiguration.addConfiguredSite(configSite);
			}
		}

		// verify we do not have 2 features with different version that
		// are configured
		checkConfiguredFeatures(newDefaultConfiguration);

		// add Activity reconciliation
		BaseSiteLocalFactory siteLocalFactory = new BaseSiteLocalFactory();
		ConfigurationActivityModel activity =
			siteLocalFactory.createConfigurationAcivityModel();
		activity.setAction(IActivity.ACTION_RECONCILIATION);
		activity.setDate(new Date());
		activity.setLabel(siteLocal.getLocationURLString());
		((InstallConfiguration) newDefaultConfiguration).addActivityModel(activity);

		// add the configuration as the currentConfig
		siteLocal.addConfiguration(newDefaultConfiguration);
		siteLocal.save();
	}

	/**
	 * 
	 */
	/*package */
	URL resolveSiteEntry(IPlatformConfiguration.ISiteEntry newSiteEntry)
		throws CoreException {
		URL resolvedURL = null;
		try {
			resolvedURL = Platform.resolve(newSiteEntry.getURL());
		} catch (IOException e) {
			throw Utilities.newCoreException(
				Policy.bind(
					"SiteLocal.UnableToResolve",
					newSiteEntry.getURL().toExternalForm()),
				e);
			//$NON-NLS-1$
		}
		return resolvedURL;
	}

	/**
	 * Compare the old state of ConfiguredSite with
	 * the 'real' features we found in Site
	 * 
	 * getSite of ConfiguredSite contains the real features found
	 * 
	 * So if ConfiguredSite.getPolicy has feature A and D as configured and C as unconfigured
	 * And if the Site contains features A,B and C
	 * We have to remove D and Configure B
	 * 
	 * We copy the oldConfig without the Features
	 * Then we loop through the features we found on teh real site
	 * If they didn't exist before we add them as configured
	 * Otherwise we use the old policy and add them to teh new configuration site
	 */
	private ConfiguredSite reconcile(IConfiguredSite oldConfiguredSite)
		throws CoreException {

		ConfiguredSite newConfiguredSite = createNewConfigSite(oldConfiguredSite);
		ConfigurationPolicy newSitePolicy = newConfiguredSite.getConfigurationPolicy();
		ConfigurationPolicy oldSitePolicy =
			((ConfiguredSite) oldConfiguredSite).getConfigurationPolicy();

		// check the Features that are still on the new version of the Config Site
		// and the new one. Add the new Features as Configured
		List toCheck = new ArrayList();
		ISite site = oldConfiguredSite.getSite();
		IFeatureReference[] foundFeatures = site.getFeatureReferences();
		IFeatureReference[] oldConfiguredFeaturesRef =
			oldConfiguredSite.getFeatureReferences();

		for (int i = 0; i < foundFeatures.length; i++) {
			boolean newFeatureFound = false;
			FeatureReferenceModel currentFeatureRefModel =
				(FeatureReferenceModel) foundFeatures[i];
			for (int j = 0; j < oldConfiguredFeaturesRef.length; j++) {
				IFeatureReference oldFeatureRef = oldConfiguredFeaturesRef[j];
				if (oldFeatureRef != null && oldFeatureRef.equals(currentFeatureRefModel)) {
					toCheck.add(oldFeatureRef);
					newFeatureFound = true;
				}
			}

			// new feature found: add as configured the policy is optimistic
			if (!newFeatureFound) {
				if (oldSitePolicy.getPolicy()
					== IPlatformConfiguration.ISitePolicy.USER_EXCLUDE) {
					newSitePolicy.addConfiguredFeatureReference(currentFeatureRefModel);
				} else {
					newSitePolicy.addUnconfiguredFeatureReference(currentFeatureRefModel);
				}
				// 

			}
		}

		// if a feature has been found in new and old state use old state (configured/unconfigured)
		Iterator featureIter = toCheck.iterator();
		while (featureIter.hasNext()) {
			IFeatureReference oldFeatureRef = (IFeatureReference) featureIter.next();
			if (oldSitePolicy.isConfigured(oldFeatureRef)) {
				newSitePolicy.addConfiguredFeatureReference(oldFeatureRef);
			} else {
				newSitePolicy.addUnconfiguredFeatureReference(oldFeatureRef);
			}
		}

		return newConfiguredSite;
	}

	/**
	 * Validate we have only one configured feature across the different sites
	 * even if we found multiples
	 * 
	 * If we find 2 features, the one with a higher version is configured
	 * If they have teh same version, the first feature is configured
	 * 
	 * This is a double loop comparison
	 * One look goes from 0 to numberOfConfiguredSites -1
	 * the other from the previous index to numberOfConfiguredSites
	 * 
	 */
	private void checkConfiguredFeatures(IInstallConfiguration newDefaultConfiguration)
		throws CoreException {

		IConfiguredSite[] configuredSites =
			newDefaultConfiguration.getConfiguredSites();

		// each configured site
		for (int indexConfiguredSites = 0;
			indexConfiguredSites < configuredSites.length;
			indexConfiguredSites++) {
			checkConfiguredFeatures(configuredSites[indexConfiguredSites]);
		}

		// Check configured sites between them
		if (configuredSites.length > 1) {
			for (int indexConfiguredSites = 0;
				indexConfiguredSites < configuredSites.length - 1;
				indexConfiguredSites++) {
				IFeatureReference[] configuredFeatures =
					configuredSites[indexConfiguredSites].getConfiguredFeatures();
				for (int indexConfiguredFeatures = 0;
					indexConfiguredFeatures < configuredFeatures.length;
					indexConfiguredFeatures++) {
					IFeatureReference featureToCompare =
						configuredFeatures[indexConfiguredFeatures];

					// compare with the rest of the configurations
					for (int i = indexConfiguredSites + 1; i < configuredSites.length; i++) {
						IFeatureReference[] possibleFeatureReference =
							configuredSites[i].getConfiguredFeatures();
						for (int j = 0; j < possibleFeatureReference.length; j++) {
							int result = compare(featureToCompare, possibleFeatureReference[j]);
							if (result != 0) {
								if (result == 1) {
									FeatureReferenceModel featureRefModel =
										(FeatureReferenceModel) possibleFeatureReference[j];
									((ConfiguredSite) configuredSites[i])
										.getConfigurationPolicy()
										.addUnconfiguredFeatureReference(featureRefModel);
								};
								if (result == 2) {
									FeatureReferenceModel featureRefModel =
										(FeatureReferenceModel) featureToCompare;
									((ConfiguredSite) configuredSites[indexConfiguredSites])
										.getConfigurationPolicy()
										.addUnconfiguredFeatureReference(featureRefModel);
									// do not break, we can continue, even if the feature is unconfigured
									// if we find another feature in another site, we may unconfigure it.
									// but it would have been unconfigured anyway because we confured another version of the same feature
									// so if teh version we find is lower than our version, by transition, it is lower then the feature that unconfigured us
								}
							}
						}
					}
				}

			}
		}
	}

	/**
	 * Validate we have only one configured feature per configured site
	 * 
	 */
	private void checkConfiguredFeatures(IConfiguredSite configuredSite)
		throws CoreException {

		ConfiguredSite cSite = (ConfiguredSite) configuredSite;
		IFeatureReference[] configuredFeatures = cSite.getConfiguredFeatures();
		ConfigurationPolicy cPolicy = cSite.getConfigurationPolicy();

		for (int indexConfiguredFeatures = 0;
			indexConfiguredFeatures < configuredFeatures.length - 1;
			indexConfiguredFeatures++) {

			IFeatureReference featureToCompare =
				configuredFeatures[indexConfiguredFeatures];

			// within the configured site
			// compare with the other configured features of this site
			for (int restOfConfiguredFeatures = indexConfiguredFeatures + 1;
				restOfConfiguredFeatures < configuredFeatures.length;
				restOfConfiguredFeatures++) {
				int result =
					compare(featureToCompare, configuredFeatures[restOfConfiguredFeatures]);
				if (result != 0) {
					if (result == 1) {
						cPolicy.addUnconfiguredFeatureReference(
							(FeatureReferenceModel) configuredFeatures[restOfConfiguredFeatures]);
					};
					if (result == 2) {
						cPolicy.addUnconfiguredFeatureReference(
							(FeatureReferenceModel) featureToCompare);
					}
				}
			}
		}
	}

	/**
	 * compare 2 feature references
	 * returns 0 if the feature are different
	 * returns 1 if the version of feature 1 is > version of feature 2
	 * returns 2 if opposite
	 */
	private int compare(
		IFeatureReference featureRef1,
		IFeatureReference featureRef2)
		throws CoreException {
		if (featureRef1 == null)
			return 0;

		IFeature feature1 = featureRef1.getFeature();
		IFeature feature2 = featureRef2.getFeature();

		if (feature1 == null || feature2 == null) {
			return 0;
		}

		VersionedIdentifier id1 = feature1.getVersionedIdentifier();
		VersionedIdentifier id2 = feature2.getVersionedIdentifier();

		if (id1 == null || id2 == null) {
			return 0;
		}

		if (id1.getIdentifier() != null
			&& id1.getIdentifier().equals(id2.getIdentifier())) {
			Version version1 = id1.getVersion();
			Version version2 = id2.getVersion();
			if (version1 != null) {
				int result = (version1.compare(version2));
				if (result == -1) {
					return 2;
				} else {
					return 1;
				}
			} else {
				return 2;
			}
		}
		return 0;
	};

	/*
	 * 
	 */
	private ConfiguredSite createNewConfigSite(IConfiguredSite oldConfiguredSiteToReconcile)
		throws CoreException {
		// create a copy of the ConfigSite based on old ConfigSite
		// this is not a clone, do not copy any features
		ConfiguredSite cSiteToReconcile = (ConfiguredSite) oldConfiguredSiteToReconcile;
		SiteModel siteModel = cSiteToReconcile.getSiteModel();
		int policy = cSiteToReconcile.getConfigurationPolicy().getPolicy();

		// copy values of the old ConfigSite that should be preserved except Features
		ConfiguredSite newConfigurationSite =
			(ConfiguredSite) new BaseSiteLocalFactory().createConfigurationSiteModel(
				siteModel,
				policy);
		newConfigurationSite.isUpdatable(cSiteToReconcile.isUpdatable());
		newConfigurationSite.setPlatformURLString(
			cSiteToReconcile.getPlatformURLString());

		return newConfigurationSite;
	}

	/*
	 * Do not cache, calculate everytime
	 * because we delete the file in SessionDelta when teh session
	 * has been seen
	 */
	private ISessionDelta[] getSessionDeltas() {
		List sessionDeltas = new ArrayList();
		IPath path = UpdateManagerPlugin.getPlugin().getStateLocation();
		InputStream in;
		InstallChangeParser parser;

		File file = path.toFile();
		if (file.isDirectory()) {
			File[] allFiles = file.listFiles();
			for (int i = 0; i < allFiles.length; i++) {
				try {
					parser = new InstallChangeParser(allFiles[i]);
					ISessionDelta change = parser.getInstallChange();
					if (change != null) {
						sessionDeltas.add(change);
					}
				} catch (Exception e) {
					if (UpdateManagerPlugin.DEBUG && UpdateManagerPlugin.DEBUG_SHOW_PARSING) {
						CoreException exc =
							Utilities.newCoreException("Unable to parse install change:" + allFiles[i], e);
						UpdateManagerPlugin.getPlugin().getLog().log(exc.getStatus());
					}
				}
			}
		}

		if (sessionDeltas.size() == 0)
			return new ISessionDelta[0];

		return (ISessionDelta[]) sessionDeltas.toArray(arrayTypeFor(sessionDeltas));
	}

	/*
	 * @see ILocalSite#displayUpdateChange()
	 */
	public void displayUpdateChange() throws CoreException {
		// find extension point
		IInstallDeltaHandler handler = null;

		String pluginID =
			UpdateManagerPlugin.getPlugin().getDescriptor().getUniqueIdentifier();
		IPluginRegistry pluginRegistry = Platform.getPluginRegistry();
		IConfigurationElement[] elements =
			pluginRegistry.getConfigurationElementsFor(
				pluginID,
				SIMPLE_EXTENSION_ID,
				INSTALL_DELTA_HANDLER);
		if (elements == null || elements.length == 0) {
			throw Utilities.newCoreException(
				Policy.bind(
					"SiteReconciler.UnableToFindInstallDeltaFactory",
					INSTALL_DELTA_HANDLER),
				null);
			//$NON-NLS-1$
		} else {
			IConfigurationElement element = elements[0];
			handler = (IInstallDeltaHandler) element.createExecutableExtension("class");
			//$NON-NLS-1$
		}

		// instanciate and open
		if (handler != null) {
			handler.init(getSessionDeltas());
			handler.open();
		}
	}

}
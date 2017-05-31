package amidst.devtools;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;

import amidst.devtools.utils.RecognisedVersionEnumBuilder;
import amidst.logging.AmidstLogger;
import amidst.mojangapi.file.DotMinecraftDirectoryNotFoundException;
import amidst.mojangapi.file.MojangApiParsingException;
import amidst.mojangapi.file.facade.MinecraftInstallation;
import amidst.mojangapi.file.json.versionlist.VersionListEntryJson;
import amidst.mojangapi.file.json.versionlist.VersionListJson;
import amidst.mojangapi.file.service.DownloadService;
import amidst.mojangapi.minecraftinterface.RecognisedVersion;

public class GenerateRecognisedVersionList {
	private final String prefix;
	private final VersionListJson versionList;
	private final MinecraftInstallation minecraftInstallation;
	private final List<String> versionsWithError = new LinkedList<>();
	private final List<String> downloadFailed = new LinkedList<>();
	private final RecognisedVersionEnumBuilder builder = RecognisedVersionEnumBuilder.createPopulated();

	public GenerateRecognisedVersionList(String prefix, String libraries, VersionListJson versionList)
			throws DotMinecraftDirectoryNotFoundException {
		this.prefix = prefix;
		this.versionList = versionList;
		this.minecraftInstallation = MinecraftInstallation
				.newCustomMinecraftInstallation(new File(libraries), null, new File(prefix), null);
	}

	public void run() {
		populate();
		print();
	}

	private void populate() {
		for (VersionListEntryJson version : versionList.getVersions()) {
			process(version);
		}
		builder.calculateMaxLength();
	}

	private void process(VersionListEntryJson version) {
		String versionId = version.getId();
		if (new DownloadService().tryDownloadClient(prefix, version)) {
			try {
				process(versionId);
			} catch (ClassNotFoundException | NoClassDefFoundError | MojangApiParsingException | IOException e) {
				e.printStackTrace();
				versionsWithError.add(versionId);
			}
		} else {
			downloadFailed.add(versionId);
		}
	}

	private void process(String versionId) throws ClassNotFoundException, MojangApiParsingException, IOException {
		AmidstLogger.info("version " + versionId);
		URLClassLoader classLoader = minecraftInstallation.newLauncherProfile(versionId).newClassLoader();
		String magicString = RecognisedVersion.generateMagicString(classLoader);
		builder.addLauncherVersionId(versionId, magicString);
	}

	private void print() {
		System.out.println();
		System.out.println();
		System.out.println();
		print("============ New", builder.renderNew());
		print("============ Renamed", builder.renderRenamed());
		print("============ Complete", builder.renderComplete());
		print("============ Error", versionsWithError);
		print("============ Download Failed", downloadFailed);
		printMessages();
	}

	private void print(String title, Iterable<String> lines) {
		System.out.println(title);
		System.out.println();
		for (String line : lines) {
			System.out.println(line);
		}
		System.out.println();
	}

	private void printMessages() {
		System.out.println();
		System.out.println(
				"If any version are listed in the error section, this might be due to missing libraries.\n"
						+ "Start the given minecraft version with the launcher.\n"
						+ "This should download the missing libraries. Afterwards, try again.");
		System.out.println();
		System.out
				.println("When copying the magic string from this output, make sure to escape all special characters.");
		System.out.println();
		System.out.println("Versions without a match are probably removed from the launcher.");
		System.out.println();
		System.out.println(
				"You might have to reorder the output, e.g. when a security fix for an old version came out, after the snapshots for the next version started.");
	}
}

package upload_gen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.zeroturnaround.zip.*;

public class Launcher {

	public static void main(String[] args) throws IOException {
		
		String path = null, username = null, password = null, targetPath = null, masterBranch = null; String branchName = args[0];
		File configFile = new File("upload_gen_config.xml");
		if (!configFile.exists()) {
			if (!createConfigFile()) {
				System.out.println("Error in creating config file. Terminating");
			}
		}
		HashMap<String,String> config = readConfig();
		path = config.get("path");
		username = config.get("username");
		password = config.get("password");
		targetPath = config.get("targetPath");
		masterBranch = config.get("masterBranch");
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		System.out.println(path + "/.git");
		Repository repository = builder.setGitDir(new File(path + "/.git")).build();
		Git git = new Git(repository);
		boolean pullComplete = updateFromRemote(git,branchName,username,password);
		if (!pullComplete) {
			System.out.println("Couldn't pull from remote. Terminating...");
			System.exit(1);
		}
		AbstractTreeIterator oldTreeParser = prepareTreeParser(repository, "refs/heads/" + masterBranch);
        AbstractTreeIterator newTreeParser = prepareTreeParser(repository, "refs/heads/" + branchName);
        List<DiffEntry> diff = null;
		try {
			diff = git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).call();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
		SimpleDateFormat timeFormat = new SimpleDateFormat("k-mm");
		String dateText = dateFormat.format(new Date());
		String timeText = timeFormat.format(new Date());
        for (DiffEntry entry : diff) {
        	String uploadPath = targetPath + "/Upload-" + dateText + "-Time-" + timeText + "/" + entry.getNewPath();
        	File file = new File(uploadPath);
        	System.out.println("Creating: " + uploadPath);
            file.getParentFile().mkdirs();
            Files.copy(Paths.get(path + "/" + entry.getNewPath()), Paths.get(uploadPath));
        }
        System.out.println("Zipping...");
        ZipUtil.pack(new File(targetPath + "/Upload-" + dateText + "-Time-" + timeText), new File(targetPath + "/Upload-" + dateText + "-Time-" + timeText + ".zip"));
        System.out.println("done.");
        repository.close();
	}
	
	private static AbstractTreeIterator prepareTreeParser(Repository repository, String ref) throws IOException,MissingObjectException,IncorrectObjectTypeException {
	// from the commit we can build the tree which allows us to construct the TreeParser
		Ref head = repository.getRef(ref);
		RevWalk walk = new RevWalk(repository);
		RevCommit commit = walk.parseCommit(head.getObjectId());
		RevTree tree = walk.parseTree(commit.getTree().getId());
		
		CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
		ObjectReader oldReader = repository.newObjectReader();
		try {
		    oldTreeParser.reset(oldReader, tree.getId());
		} finally {
		    oldReader.release();
		}
		
		walk.dispose();
		
		return oldTreeParser;
	}
	
	private static HashMap<String,String> readConfig() {
		Document dom = null; HashMap<String,String> map = new HashMap<String,String>();
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			dom = db.parse("upload_gen_config.xml");
			Element root = dom.getDocumentElement();
			String path = root.getElementsByTagName("path").item(0).getChildNodes().item(0).getNodeValue();
			map.put("path",path);
			String username = root.getElementsByTagName("username").item(0).getChildNodes().item(0).getNodeValue();
			map.put("username", username);
			String password = root.getElementsByTagName("password").item(0).getChildNodes().item(0).getNodeValue();
			map.put("password",password);
			String targetPath = root.getElementsByTagName("targetPath").item(0).getChildNodes().item(0).getNodeValue();
			map.put("targetPath",targetPath);
			String masterBranch = root.getElementsByTagName("masterBranch").item(0).getChildNodes().item(0).getNodeValue();
			map.put("masterBranch",masterBranch);
		} catch(ParserConfigurationException pce) {
			pce.printStackTrace();
		} catch(SAXException se) {
			se.printStackTrace();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
		return map;
	}
	
	private static boolean createConfigFile() {
		try {
			BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Enter repository path: ");
			String path = bf.readLine();
			System.out.println("Enter username: ");
			String username = bf.readLine();
			System.out.println("Enter password: ");
			String password = bf.readLine();
			System.out.println("Enter target path: ");
			String targetPath = bf.readLine();
			System.out.println("Enter remote branch name: ");
			String masterBranch = bf.readLine();
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("settings");
			Element pathElement = doc.createElement("path");
			Element usernameElement = doc.createElement("username");
			Element passwordElement = doc.createElement("password");
			Element targetPathElement = doc.createElement("targetPath");
			Element masterBranchElement = doc.createElement("masterBranch");
			masterBranchElement.setTextContent(masterBranch);
			targetPathElement.setTextContent(targetPath);
			pathElement.setTextContent(path);
			usernameElement.setTextContent(username);
			passwordElement.setTextContent(password);
			rootElement.appendChild(pathElement);
			rootElement.appendChild(usernameElement);
			rootElement.appendChild(passwordElement);
			rootElement.appendChild(targetPathElement);
			rootElement.appendChild(masterBranchElement);
			doc.appendChild(rootElement);
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(new File("upload_gen_config.xml"));
			transformer.transform(source, result);
			System.out.println("File saved!");
		} catch(IOException e) {
			e.printStackTrace();
			return false;
		} catch(ParserConfigurationException e) {
			e.printStackTrace();
			return false;
		} catch(TransformerException e) {
			e.printStackTrace();
		}
		return true;
	}
	
	private static boolean updateFromRemote(Git git, String branchName, String username, String password) {
		boolean pull1 = false, pull2 = false;
		try {
			CheckoutCommand checkout = git.checkout();
			System.out.println("Pulling master into master ...");
			checkout.setName("master").call();
			PullCommand pullCommand = git.pull(); PullResult pullResult = null;
			CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(username,password);
			pullCommand.setRemoteBranchName("master");
			pullResult = pullCommand.setCredentialsProvider(credentialsProvider).call();
			if (pullResult.isSuccessful()) { 
				System.out.println("Done!");
				pull2 = true;
			}
			checkout = git.checkout();
			checkout.setName(branchName).call();
			System.out.println("Pulling master into " + branchName + "...");
			pullResult = pullCommand.setCredentialsProvider(credentialsProvider).call();
			if (pullResult.isSuccessful()) { 
				System.out.println("Done!");
				pull1 = true;
			}
		} catch (GitAPIException e1) {
			e1.printStackTrace();
		}
		if (pull1 == true && pull2 == true) {
			return true;
		} else {
			return false;
		}
	}
}

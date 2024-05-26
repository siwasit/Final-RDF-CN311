package com.sf311;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Property;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.*;

public class Server {
    public static void main(String[] args) throws IOException {
        try {
            // Create receive folder
            String receiveFolder = "src/main/java/com/sf311/received";
            Path receivePath = Paths.get(receiveFolder);
            if (!Files.exists(receivePath)) {
                createPath(receiveFolder);
            }

            ServerSocket serverSocket = new ServerSocket(9999);
            System.out.println("Server started. Waiting for a client to connect...");

            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected.");

            // Server receive zip file from the client
            InputStream in = clientSocket.getInputStream();
            FileOutputStream fos = new FileOutputStream(receiveFolder + "/received_file.zip");

            byte[] buffer = new byte[1024];
            int bytesRead;
            while (true) {
                bytesRead = in.read(buffer);
                if (bytesRead == 1024) {
                    fos.write(buffer, 0, bytesRead);
                } else {
                    if (bytesRead == 0)
                        break;
                    else {
                        fos.write(buffer, 0, bytesRead);
                        break;
                    }
                }
            }

            System.out.println("File received from client and saved into received folder.");

            String csvFolderPath = "src/main/java/com/sf311/csv";
            File folder = new File(csvFolderPath);

            // Put the files in the zip into the csv file.
            extractFromZip("src/main/java/com/sf311/received/received_file.zip", csvFolderPath);
            System.out.println("Files extracted successfully into csv folder.");

            String message = "";
            // Start of csv files modifying
            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles();
                String[] filePaths = new String[files.length];

                for (int i = 0; i < files.length; i++) {
                    filePaths[i] = files[i].getAbsolutePath();
                }

                // int threadNum = 8;
                int[] numThreadList = { 2, 4, 6, 8, 16 };
                for (int threadNum : numThreadList) {
                    ExecutorService executor = Executors.newFixedThreadPool(threadNum);

                    long startTime = System.nanoTime();
                    for (String path : filePaths) {
                        // Submit each file processing task to the thread pool
                        executor.submit(new CSVProcessor(path));
                    }

                    executor.shutdown();
                    try {
                        // Wait for all tasks to finish or timeout after a certain period
                        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        System.err.println("Error waiting for thread pool termination: " + e.getMessage());
                        e.printStackTrace();
                    }

                    System.out.println("All threads have completed. Proceeding to the next step.");
                    long endTime = System.nanoTime(); // Record the end time
                    long runTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                    System.out.println("Using " + threadNum + " threads, process completed in " + runTime + " ms.");

                    File tllFolder = new File("src/main/java/com/sf311/output");
                    File[] tllFiles = tllFolder.listFiles();
                    for (File tllFile : tllFiles) {
                        // System.out.println("Deleted " + tllFile.getName());
                        tllFile.delete();
                    }

                    System.out.println("Deleted tll files from " + threadNum + " threads' process.");

                    message += "Using " + threadNum + " threads: runtime = " + String.valueOf(runTime) + " ms.,";

                }

                // Runtime of no thread process
                long startTimeSync = System.nanoTime();
                for (String path : filePaths) {

                    TripleManagement tripleManager = new TripleManagement(path);
                    List<TripleManagement.Triple> triples = null;

                    try {
                        triples = tripleManager.getTripleList();
                    } catch (IOException e) {
                        System.err.println("Error reading the CSV file: " + e.getMessage());
                        e.printStackTrace();
                    }

                    String turtle = createRDFModel(triples);
                }

                long endTimeSync = System.nanoTime(); // Record the end time
                long durationInMillisSync = TimeUnit.NANOSECONDS.toMillis(endTimeSync -
                        startTimeSync);
                System.out.println("Total execution time for no thread process: " + durationInMillisSync +
                        " ms");
                message += "Using no thread: runtime = " + durationInMillisSync + " ms.";

            } else {
                System.out.println("The specified folder does not exist or is not a directory.");
                message = "Error";
            }

            OutputStream output = clientSocket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            writer.println(message);
            System.out.println("Sent out String: " + message);

            String path = "src/main/java/com/sf311/outputZip";
            Path p = Paths.get(path);

            if (!Files.exists(p))
                createPath(path);

            String source = "src/main/java/com/sf311/output";
            String zipOutput = "src/main/java/com/sf311/outputZip/output.zip";
            // String zipOutput = "output.zip";

            toZip(source, zipOutput);
            System.out.println("Source successfully zipped at outputZip folder.");

            // Server send out the finished file to the client
            File fileToSend = new File("src/main/java/com/sf311/outputZip/output.zip");
            OutputStream out = clientSocket.getOutputStream();
            FileInputStream fis = new FileInputStream(fileToSend);
            while ((bytesRead = fis.read(buffer)) >= 0) {
                out.write(buffer, 0, bytesRead);
            }
            System.out.println("File sent to client.");

            fos.close();
            fis.close();
            out.close();
            clientSocket.close();
            serverSocket.close();

            // Delete used zip file
            File f = new File(receiveFolder + "/received_file.zip");
            // System.out.println("Deleted " + f.getName());
            System.out.println("Deleted all files in received folder.");
            f.delete();

            f = new File("src/main/java/com/sf311/outputZip/output.zip");
            // System.out.println("Deleted " + f.getName());
            System.out.println("Deleted the file output.zip.");
            f.delete();

            // Delete all used csv files
            File csvFolder = new File("src/main/java/com/sf311/csv");
            File[] csvFiles = csvFolder.listFiles();
            for (File csvFile : csvFiles) {
                // System.out.println("Deleted " + csvFile.getName());
                csvFile.delete();
            }
            System.out.println("Deleted all files in csv folder.");

            // Delete all used tll files
            File tllFolder = new File("src/main/java/com/sf311/output");
            File[] tllFiles = tllFolder.listFiles();
            for (File tllFile : tllFiles) {
                // System.out.println("Deleted " + tllFile.getName());
                tllFile.delete();
            }
            System.out.println("Deleted all files in output folder.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void extractFromZip(String zipFilePath, String extractFolderPath) throws IOException {
        File folder = new File(extractFolderPath);
        if (!folder.exists()) {
            System.out.println("Created folder csv");
            folder.mkdirs(); // Create the destination folder if it doesn't exist
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                String[] parts = entryName.split("/");
                String entryPath = extractFolderPath + "/" + parts[parts.length - 1];
                System.out.println(entryPath);
                if (!entry.isDirectory()) {
                    File entryFile = new File(entryPath);
                    entryFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                } else {
                    File dir = new File(entryPath);
                    dir.mkdirs();
                }
                zis.closeEntry();
            }
        }
    }

    public static void createPath(String path) {
        try {
            Path p = Paths.get(path);
            Files.createDirectories(p);
            System.out.println("Created a path");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void toZip(String source, String zipFilePath) throws IOException {
        File sourceDir = new File(source);
        File[] files = sourceDir.listFiles();
        FileOutputStream fos = new FileOutputStream(zipFilePath);
        try (ZipOutputStream zos = new ZipOutputStream(fos)) {
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        try (FileInputStream fis = new FileInputStream(file)) {
                            ZipEntry zipEntry = new ZipEntry(file.getName());
                            zos.putNextEntry(zipEntry);
                            byte[] bytes = new byte[1024];
                            int length;
                            while ((length = fis.read(bytes)) >= 0) {
                                zos.write(bytes, 0, length);
                            }
                            zos.closeEntry();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                System.out.println("The output is empty.");
            }
        }
    }

    public static String createRDFModel(List<TripleManagement.Triple> triples) {
        Model model = ModelFactory.createDefaultModel();
        String exNS = "http://example.org/";
        String foafNS = "http://xmlns.com/foaf/0.1/";
        model.setNsPrefix("ex", exNS);
        model.setNsPrefix("foaf", foafNS);

        for (TripleManagement.Triple triple : triples) {
            String subject = triple.getFirst().replaceAll("[\\s']+", "_").replaceAll("\\([^()]*\\)", "");
            String predicate = triple.getSecond();
            String object = triple.getThird();
            String nsObject = triple.getThird().replaceAll("[\\s']+", "_").replaceAll("\\([^()]*\\)", "");

            Resource subjectResource = model.createResource(exNS + subject);
            Property predicateProperty = model.createProperty(foafNS + predicate);
            Literal literalValue = model.createTypedLiteral(object, XSDDatatype.XSDstring);
            Resource objectResource = model.createResource(exNS + nsObject);

            boolean ObjectTypeExists = model.contains(subjectResource, RDF.type, "Object");
            if (!ObjectTypeExists) {
                model.add(subjectResource, RDF.type, "Object");
            }

            if (predicate.equals("HasAttribute")) {
                model.add(subjectResource, predicateProperty, literalValue);
            } else {
                model.add(subjectResource, predicateProperty, objectResource);
            }
        }

        // Write the RDF model to a StringWriter
        StringWriter stringWriter = new StringWriter();
        model.write(stringWriter, "TURTLE");

        String randomFileName = UUID.randomUUID().toString() + ".ttl";
        String folderPath = "src/main/java/com/sf311/output/";
        try {
            // Output stream to write the Turtle data
            OutputStream outputStream = new FileOutputStream(folderPath + randomFileName);

            // Write the model in Turtle format to the output stream
            model.write(outputStream, "TURTLE");

            // Close the output stream
            outputStream.close();

            System.out.println(Thread.currentThread().getName() + ": Turtle file saved successfully with the name: "
                    + randomFileName);
        } catch (Exception e) {
            System.err.println("Error saving Turtle file: " + e.getMessage());
        }

        // Return the Turtle representation as a String
        return stringWriter.toString();
    }
}

class TripleManagement {
    private String path;

    public TripleManagement(String path) {
        this.path = path;
    }

    public List<Triple> getTripleList() throws IOException {
        ArrayList<Triple> triples = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] data = line.split(",");
                Triple triple = new Triple(data[0], data[1], data[2]); // Assuming CSV has at least 3 values
                triples.add(triple);
            }
        }
        return triples;
    }

    static class Triple {
        private String first;
        private String second;
        private String third;

        public Triple(String first, String second, String third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }

        public String getFirst() {
            return first;
        }

        public String getSecond() {
            return second;
        }

        public String getThird() {
            return third;
        }

        @Override
        public String toString() {
            return "[" + first + ", " + second + ", " + third + "]";
        }
    }

}

class CSVProcessor implements Runnable {
    private String filePath;

    public CSVProcessor(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public void run() {
        TripleManagement tripleManager = new TripleManagement(filePath);
        List<TripleManagement.Triple> triples = null;

        try {
            triples = tripleManager.getTripleList();
            String turtle = Server.createRDFModel(triples); // Call createRDFModel from Main
        } catch (IOException e) {
            System.err.println("Error reading the CSV file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
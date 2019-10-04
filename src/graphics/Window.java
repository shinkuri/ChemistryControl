package graphics;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EtchedBorder;

import com.google.gson.Gson;

import craftingQueuePanel.CraftingQueuePanel;
import data_loader.GSONLoader;
import fluidDisplayPanel.FluidDisplayPanel;
import logic.Item;
import logic.Recipe;
import logic.RecipesJSON;

@SuppressWarnings("serial")
public class Window extends JFrame {
	
	public static final String URL_PREFIX = "http://localhost:8000";
	
	public static void main(String[] args) {
		
		final GSONLoader gsonLoader = new GSONLoader();
		final RecipesJSON dataJSON = gsonLoader.parseLocalJSON("chemicals.txt", RecipesJSON.class);
		final HashMap<String, Recipe> recipes = new HashMap<>();
		for (Recipe recipe : dataJSON.recipes) {
			recipes.put(recipe.result, recipe);
		}
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException ex) {
			ex.printStackTrace();
		}
		
		final Window gui = new Window();
		gui.setDefaultCloseOperation(EXIT_ON_CLOSE);
		gui.setTitle("Crafter " + "V0.1");
		gui.setSize(1400, 900);
		gui.setResizable(false);
		gui.setLayout(null);
		gui.getContentPane().setBackground(Color.GRAY);
		
		final InfoPanel infoPanel = new InfoPanel(gui.getWidth(), gui.getHeight());
		infoPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		infoPanel.setVisible(true);
		gui.add(infoPanel);
		
		final MenuBar menuBar = new MenuBar();
		gui.setJMenuBar(menuBar);
		
		String[] recipeNames = new String[recipes.keySet().size()];
		int i = 0;
		for(String recipeName : recipes.keySet()) {
			recipeNames[i++] = recipeName;
		}
		final JComboBox<String> recipePicker = new JComboBox<String>(recipeNames);
		recipePicker.setLocation(11, 11);
		recipePicker.setSize(209, 30);
		recipePicker.setVisible(true);
		gui.add(recipePicker);
		
		final JTextArea recipeInfo = new JTextArea();
		recipeInfo.setLocation(240, 11);
		recipeInfo.setSize(500, 250);
		recipeInfo.setVisible(true);
		recipeInfo.setEditable(false);
		recipeInfo.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED, Color.GRAY, Color.GRAY));
		recipeInfo.setBackground(Color.LIGHT_GRAY);
		gui.add(recipeInfo);
		
		final JTextArea requestInfo = new JTextArea();
		requestInfo.setLocation(240, 270);
		requestInfo.setSize(500, 250);
		requestInfo.setVisible(true);
		requestInfo.setEditable(false);
		requestInfo.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED, Color.GRAY, Color.GRAY));
		requestInfo.setBackground(Color.LIGHT_GRAY);
		gui.add(requestInfo);
		
		final JTextField entryField = new JTextField();
		entryField.setLocation(11, 270);
		entryField.setSize(100, 30);
		entryField.setVisible(true);
		gui.add(entryField);
		
		final JButton calcButton = new JButton();
		calcButton.setLocation(120, 270);
		calcButton.setSize(100, 30);
		calcButton.setText("Calculate");
		calcButton.setVisible(true);
		gui.add(calcButton);
		
		final JButton craftButton = new JButton();
		craftButton.setLocation(120, 310);
		craftButton.setSize(100, 30);
		craftButton.setText("Craft");
		craftButton.setVisible(true);
		gui.add(craftButton);
		
		final FluidDisplayPanel fluidDisplayPanel = 
				new FluidDisplayPanel(1030, 11, 300, 600);
		fluidDisplayPanel.setVisible(true);
		gui.add(fluidDisplayPanel);
		
		final Thread fluidDisplayThread = new Thread(fluidDisplayPanel);
		fluidDisplayThread.setName("Fluid Display Panel Updater");
		fluidDisplayThread.start();
		
		final CraftingQueuePanel craftingQueuePanel =
				new CraftingQueuePanel(800, 11, 200, 300);
		craftingQueuePanel.setVisible(true);
		gui.add(craftingQueuePanel);
		
		final Thread craftingQueueThread = new Thread(craftingQueuePanel);
		craftingQueueThread.setName("Crafting Queue Display Panel Updater");
		craftingQueueThread.start();
		
		recipePicker.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final StringBuilder sb = new StringBuilder();
				final Recipe recipe = recipes.get(recipePicker.getSelectedItem());
				
				sb.append("Integrated Circuit: " + recipe.circuit);
				sb.append("\n");
				if(recipe.input_fluid != null) {
					sb.append("\nInput Fluids:\n");
					for(Item item : recipe.input_fluid) {
						sb.append(item.name + ": " + item.amount + "L\n");
					}					
				}
				if(recipe.input_item != null) {
					sb.append("\nInput Items:\n");
					for(Item item : recipe.input_item) {
						sb.append(item.name + ": " + item.amount + "\n");
					}					
				}
				if(recipe.output_fluid != null) {
					sb.append("\nOutput Fluids:\n");
					for(Item item : recipe.output_fluid) {
						sb.append(item.name + ": " + item.amount + "L\n");
					}					
				}
				if(recipe.output_item != null) {
					sb.append("\nOutput Items:\n");
					for(Item item : recipe.output_item) {
						sb.append(item.name + ": " + item.amount + "\n");
					}					
				}
				
				recipeInfo.setText(sb.toString());
			}
		});
		
		// CRAFTING REQUESTS
		
		final Recipe lastCalculatedBatch = new Recipe();
		
		calcButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final Recipe recipe = recipes.get(recipePicker.getSelectedItem());
				
				try {
					final int amountRequested = Integer.parseInt(entryField.getText());	
					// one of the two queries will be 0, the other one holds the number I want
					final int recipeOutputAmount = recipe.getAmountOfResult(recipe.result);
					final int outputMultiplier = (int) Math.ceil((amountRequested + 0f) / recipeOutputAmount);
					final int amountPlanned = outputMultiplier * recipeOutputAmount;
					
					requestInfo.append("Requested: " + recipe.result + "\n");
					requestInfo.append("Amount requested: " + amountRequested + "\n");
					requestInfo.append("Amount planned: " + amountPlanned + "\n\n");
					// cache batch recipe
					lastCalculatedBatch.circuit = recipe.circuit;
					lastCalculatedBatch.result = recipe.result;
					final Item[] inputFluids = new Item[recipe.getInputFluidsLength()];
					final Item[] inputItems = new Item[recipe.getInputItemsLength()];
					final Item[] outputFluids = new Item[recipe.getOutputFluidsLength()];
					final Item[] outputItems = new Item[recipe.getOutputItemsLength()];
					for(int i = 0; i < inputFluids.length; i++) {
						inputFluids[i] = new Item(
								recipe.input_fluid[i].name,
								recipe.input_fluid[i].amount * outputMultiplier);
					}
					for(int i = 0; i < inputItems.length; i++) {
						inputItems[i] = new Item(
								recipe.input_item[i].name,
								recipe.input_item[i].amount * outputMultiplier);
					}
					for(int i = 0; i < outputFluids.length; i++) {
						outputFluids[i] = new Item(
								recipe.output_fluid[i].name,
								recipe.output_fluid[i].amount * outputMultiplier);
					}
					for(int i = 0; i < outputItems.length; i++) {
						outputItems[i] = new Item(
								recipe.output_item[i].name,
								recipe.output_item[i].amount * outputMultiplier);
					}
					lastCalculatedBatch.input_fluid = inputFluids;
					lastCalculatedBatch.input_item = inputItems;
					lastCalculatedBatch.output_fluid = outputFluids;
					lastCalculatedBatch.output_item = outputItems;
					
				} catch (NumberFormatException ex) {
					infoPanel.updateError("Requested amount is not a number");
				}
			}
		});
		
		craftButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				HttpURLConnection con = null;
				try {
					final URL url = new URL(URL_PREFIX + "/crafting_request");
					con = (HttpURLConnection) url.openConnection();
					con.setRequestMethod("POST");
					con.setConnectTimeout(5000);
					con.setReadTimeout(5000);
					
					con.setDoOutput(true);
					con.setDoInput(true);
					final DataOutputStream dos = new DataOutputStream(con.getOutputStream());
					
					final RecipesJSON recipesJSON = new RecipesJSON();
					recipesJSON.recipes = new Recipe[1];
					recipesJSON.recipes[0] = lastCalculatedBatch;
					final Gson gson = new Gson();
					final String jsonString = gson.toJson(recipesJSON, RecipesJSON.class);
					dos.write(jsonString.getBytes());
					dos.flush();
					dos.close();
					
					//infoPanel.updateInfo("Sent crafting request. Status: " + con.getResponseCode());
					
					final BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
					final StringBuilder sb = new StringBuilder();
					String line;
					while((line = br.readLine()) != null) {
						sb.append(line);
					}
					
					System.out.println("Missing Inputs: " + sb.toString());
				} catch (MalformedURLException ex) {
					ex.printStackTrace();
				} catch (ProtocolException ex) {
					ex.printStackTrace();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});
		
		gui.setVisible(true);
		
	}

}

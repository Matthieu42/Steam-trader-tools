package com.matthieu42.steamtradertools.model;


import com.github.goive.steamapi.data.SteamApp;
import com.github.goive.steamapi.exceptions.SteamApiException;
import com.matthieu42.steamtradertools.model.key.SteamKey;
import com.matthieu42.steamtradertools.model.steamapp.AbstractSteamAppWithKey;
import com.matthieu42.steamtradertools.model.steamapp.LinkedSteamAppWithKey;
import com.matthieu42.steamtradertools.model.steamapp.NotLinkedSteamAppWithKey;
import javafx.concurrent.Task;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import java.io.*;
import java.util.TreeSet;
import java.util.prefs.Preferences;

/**
 * Created by Matthieu on 07/03/2017.
 */

@XmlRootElement(name = "UserAppList")
@XmlAccessorType(XmlAccessType.FIELD)
public class UserAppList
{
    @XmlElementWrapper(name = "steamApps")
    @XmlElement(name = "steamApp")
    private TreeSet<AbstractSteamAppWithKey> appList;
    @XmlElement(name = "nbTotalKey")
    private int nbTotalKey;


    public UserAppList()
    {
        appList = new TreeSet<>();
        nbTotalKey = 0;
    }

    public void addApp(AbstractSteamAppWithKey app)
    {
        this.appList.add(app);
    }

    public void delApp(AbstractSteamAppWithKey app)
    {
        this.appList.remove(app);
    }

    public TreeSet<AbstractSteamAppWithKey> getAppList()
    {
        return appList;
    }

    public void updateNbTotalKey()
    {
        int total = 0;
        for(AbstractSteamAppWithKey app : this.appList)
        {
            total += app.getNbTotalKey();
        }
        this.nbTotalKey = total;
    }


    public int getNbTotalKey()
    {
        return nbTotalKey;
    }

    public int getNbTotalApp()
    {
        return appList.size();
    }

    public void setAppList(TreeSet<AbstractSteamAppWithKey> appList) {
        this.appList = appList;
    }

    public void loadFromXml(File file) throws JAXBException, SteamApiException
    {
        if(file != null)
        {
                JAXBContext context = JAXBContext.newInstance(this.getClass());
                Unmarshaller um;
                um = context.createUnmarshaller();
                Preferences prefs = Preferences.userNodeForPackage(com.matthieu42.steamtradertools.controller.AppController.class);
                prefs.put(PreferencesKeys.SAVE_PATH.toString(),file.getAbsolutePath());
                UserAppList loadedList = (UserAppList) um.unmarshal(file);
                this.setAppList(loadedList.getAppList());
                this.nbTotalKey = loadedList.nbTotalKey;


        }
    }
    public void saveToXml(File file) throws JAXBException
    {
        JAXBContext context;
        context = JAXBContext.newInstance(this.getClass());
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        marshaller.marshal(this, file);

    }

    public Task<Void> importFromCSV(File csv) throws IOException
    {
        return new Task<Void>()
        {
            @Override
            public Void call() throws InterruptedException, SteamApiException, IOException
            {
                int nbLines = CountLine.countLines(csv.getAbsolutePath()) + 1;
                try (BufferedReader br = new BufferedReader(new FileReader(csv)))
                {
                    String line;
                    TreeSet<AbstractSteamAppWithKey> appList = new TreeSet<>();
                    int progress = 0;
                    while ((line = br.readLine()) != null)
                    {
                        Task<AbstractSteamAppWithKey> importLine = importLineFromCSV(line);
                        importLine.setOnSucceeded(t ->
                                appList.add(importLine.getValue()));
                        importLine.run();
                        progress++;
                        updateProgress(progress,nbLines);

                    }

                    UserAppList newList = new UserAppList();
                    newList.setAppList(appList);
                    try
                    {
                        newList.saveToXml(new File("importedData.xml"));
                    } catch (JAXBException e)
                    {
                        e.printStackTrace();
                    }

                }
                return null;
            }

        };

    }

    public Task<AbstractSteamAppWithKey> importLineFromCSV(String line) throws IOException{
        return new Task<AbstractSteamAppWithKey>()
        {
            @Override
            public AbstractSteamAppWithKey call() throws InterruptedException, SteamApiException, IOException
            {
                String[] values = line.split(";");
                /*Try to link the app */
                AbstractSteamAppWithKey newApp;
                try
                {
                    SteamApp app = SteamApiStatic.steamApi.retrieve(values[0]);
                    /*Successful link */
                    Boolean tradingCards = false;
                    for( String s : app.getCategories())
                    {
                        if(s.equals("Steam Trading Cards"))
                            tradingCards = true;
                    }
                    Boolean achievement = false;
                    for( String s : app.getCategories())
                    {
                        if(s.equals("Steam Achievements"))
                            achievement = true;
                    }
                    newApp = new LinkedSteamAppWithKey(values[0],Integer.parseInt(app.getAppId()), achievement, tradingCards, app.getHeaderImage(), app.getPrice());
                    appList.add(newApp);

                } catch (SteamApiException e)
                {
                    /*Can't link */
                    newApp = new NotLinkedSteamAppWithKey(values[0]);
                    appList.add(newApp);
                }
                /*Adding the keys */
                for (int i = 1; i < values.length; i++)
                {
                    newApp.addKey(values[i]);
                }

                return newApp;
            }

        };

    }
    public String exportAsNameString(){
        StringBuilder list = new StringBuilder();
        for (AbstractSteamAppWithKey a : appList){
            list.append(a.getName());
            list.append('\n');
        }
        list.deleteCharAt(list.lastIndexOf("\n"));
        return list.toString();
    }

    public String exportAsLinkNameString()
    {
        StringBuilder list = new StringBuilder();
        for (AbstractSteamAppWithKey a : appList){
            list.append("- ");
            if(a instanceof LinkedSteamAppWithKey){
            list.append("[");
            list.append(a.getName());
            list.append("](http://store.steampowered.com/app/");
            list.append(((LinkedSteamAppWithKey) a).getId());
            list.append("/)");
            }
            else {
                list.append(a.getName());
            }
            list.append('\n');
        }
        list.deleteCharAt(list.lastIndexOf("\n"));
        return list.toString();
    }

    public TreeSet<AbstractSteamAppWithKey> getGamesWithUsedKey(){
        TreeSet<AbstractSteamAppWithKey> usedResult = new TreeSet<>();
        for (AbstractSteamAppWithKey steamApp : this.getAppList())
        {
            for(SteamKey key : steamApp.getSteamKeyList()){
                if(key.isUsed()) {
                    usedResult.add(steamApp);
                    break;
                }

            }
        }
        return usedResult;
    }
}

package org.powertac.samplebroker.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;


public class ClassPathHacker
{
  public static void loadJarDynamically ()
  {
    String jarPath = BrokerMain.class.getProtectionDomain()
        .getCodeSource().getLocation().getFile();
    URLClassLoader ucl = (URLClassLoader) ClassLoader.getSystemClassLoader();
    boolean found = false;
    for (URL url : ucl.getURLs()) {
      if (url.getFile().equals(jarPath)) {
        found = true;
        break;
      }
    }

    if (!found) {
      ClassPathHacker.addFile(jarPath);
    }
  }

  private static void addFile (final String s)
  {
    try {
      addURL((new File(s)).toURI().toURL());
    }
    catch (IOException ignored) {
      System.out.println();
      System.out.println("Unable to load " + s + "dynamically, exiting!");
      System.out.println();
      System.exit(1);
    }
  }

  private static void addURL (final URL u) throws IOException
  {
    final URLClassLoader urlClassLoader = getUrlClassLoader();

    try {
      final Method method = getAddUrlMethod();
      method.setAccessible(true);
      method.invoke(urlClassLoader, u);
    }
    catch (final Exception e) {
      throw new IOException("Error, could not add URL to system classloader");
    }
  }

  private static Method getAddUrlMethod () throws NoSuchMethodException
  {
    final Class<URLClassLoader> urlclassloader = URLClassLoader.class;
    return urlclassloader.getDeclaredMethod("addURL", new Class[]{URL.class});
  }

  private static URLClassLoader getUrlClassLoader ()
  {
    final ClassLoader sysloader = ClassLoader.getSystemClassLoader();
    if (sysloader instanceof URLClassLoader) {
      return (URLClassLoader) sysloader;
    }
    else {
      throw new IllegalStateException("Not an UrlClassLoader: " + sysloader);
    }
  }
}
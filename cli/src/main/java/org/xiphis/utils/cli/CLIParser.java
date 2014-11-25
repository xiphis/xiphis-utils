/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.xiphis.utils.cli;

import org.xiphis.utils.common.BadArgumentException;
import org.xiphis.utils.common.Callback;
import org.xiphis.utils.common.Configure;
import org.xiphis.utils.common.Filter;
import org.xiphis.utils.common.Setable;
import org.xiphis.utils.common.Utils;
import org.xiphis.utils.common.ParserException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.Format;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author atcurtis
 * @since 2014-11-16
 */
public class CLIParser implements Configure
{
  public final static Filter<List<Class<?>>, Class<?>> NO_RECURSE = ignored -> Collections.emptyList();

  private final Set<Class<?>> _registered;
  private final IdentityHashMap<Class<? extends Format>, Format> _formats;
  private final HashMap<String, Group> _groups;
  private final LinkedList<Arg<?>> options;
  private final LinkedList<Apply<?>> args;
  private final Group _main;

  /**
   * Constructor.
   */
  public CLIParser()
  {
    _registered = Collections.newSetFromMap(new IdentityHashMap<>());
    _formats = new IdentityHashMap<>();
    _groups = new HashMap<>();
    options = new LinkedList<>();
    args = new LinkedList<>();
    _main = new Group();
  }

  /**
   * Clear all data which was parsed from calling {@link #parse(String[])} or
   * {@link #parse(String[], org.xiphis.utils.common.Callback)}.
   */
  public void reset()
  {
    args.clear();
  }

  /**
   * Print the help text to the {@link HelpFormatter} instance.
   * @param out Help formatter.
   */
  public void printHelp(HelpFormatter out)
  {
    printGroupHeader(_main, out);
    out.printTitle("Options");
    printGroupOptions(_main, out);
    String[] groupNames = _groups.keySet().toArray(new String[_groups.size()]);
    Arrays.sort(groupNames);
    for (String key : groupNames)
    {
      Group group = _groups.get(key);
      out.printTitle(key);
      printGroupHeader(group, out);
      printGroupOptions(group, out);
      printGroupFooter(group, out);
    }
    printGroupFooter(_main, out);
  }

  protected void printGroupHeader(Group group,HelpFormatter out)
  {
    for (CLIHeader header : group._headers)
      out.printText(header.value());
  }

  protected void printGroupFooter(Group group, HelpFormatter out)
  {
    for (CLIFooter footer : group._footers)
      out.printText(footer.value());
  }

  protected void printGroupOptions(Group group, HelpFormatter out)
  {
    Arg[] options = group._options.toArray(new Arg[group._options.size()]);
    Arrays.sort(options, (o1, o2) -> {
      if (o1.shortName != null && o2.shortName != null)
      {
        int res = Character.compare(o1.shortName, o2.shortName);
        if (res != 0 || (o1.longName == null && o2.longName == null))
          return res;
      }
      if (o1.shortName != null && o2.longName != null)
      {
        int res = Character.compare(o1.shortName, o2.longName.charAt(0));
        if (res != 0 || o1.longName == null)
          return res;
      }
      if (o2.shortName != null && o1.longName != null)
      {
        int res = Character.compare(o1.longName.charAt(0), o2.shortName);
        if (res != 0 || o2.longName == null)
          return res;
      }
      return o1.longName.compareTo(o2.longName);
    });
    for (Arg a : options)
    {
      out.printOption(a.longName, a.shortName, !a.noArgument() ? a.argName : null,
                      a.desc != null ? a.desc.value() : "");
    }
  }

  protected Group getGroup(CLIGroup group, Group defaultGroup)
  {
    if (group != null)
    {
      Group found = _groups.get(group.value());
      if (found == null)
      {
        found = new Group();
        _groups.put(group.value(), found);
      }
      return found;
    }
    return defaultGroup;
  }

  protected Format getFormat(CLIFormat format, CLIMessageFormat messageFormat)
  {
    if (format != null)
    {
      if (messageFormat != null)
        throw new ParserException("Cannot have both CLIFormat and CLIMessageFormat annotation");

      Format found = _formats.get(format.value());
      if (found == null)
      {
        try
        {
          found = format.value().newInstance();
          _formats.put(format.value(), found);
        }
        catch (InstantiationException e)
        {
          throw new ParserException(e.getCause());
        }
        catch (IllegalAccessException e)
        {
          throw new ParserException(e);
        }
      }
      return found;
    }
    if (messageFormat != null)
    {
      return new MessageFormat(messageFormat.value());
    }
    return null;
  }


  /**
   * Examines the supplied class for CLI annotations and stores it.
   * This metadata is later used to parse the command line.
   *
   * @param initialClazz Class to be examined.
   * @param <T> Type of class.
   * @return Class examined.
   */
  public <T> Class<T> register(Class<T> initialClazz)
  {
    return register(initialClazz, NO_RECURSE);
  }

  public <T> Class<T> register(Class<T> initialClazz, Filter<List<Class<?>>, Class<?>> recurseFilter)
  {
    Set<Class<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    LinkedList<Class<?>> pending = new LinkedList<>();
    pending.add(initialClazz);
    Class<?> clazz;
    while ((clazz = pending.pollFirst()) != null)
    {
      if (seen.contains(clazz) || _registered.contains(clazz))
        continue;
      seen.add(clazz);

      CLIHeader header = clazz.getAnnotation(CLIHeader.class);
      CLIFooter footer = clazz.getAnnotation(CLIFooter.class);
      Group clazzGroup = getGroup(clazz.getAnnotation(CLIGroup.class), _main);

      List<Arg<?>> args = new LinkedList<>();

      for (Field field : clazz.getFields())
      {
        int modifiers = field.getModifiers();
        if (!Modifier.isPublic(modifiers) || field.getAnnotations().length == 0) continue;

        if (field.getAnnotation(CLIConfigure.class) != null)
          pending.add(field.getType());

        CLIDescription description = field.getAnnotation(CLIDescription.class);
        CLIGroup cliGroup = field.getAnnotation(CLIGroup.class);
        CLILongName cliLongName = field.getAnnotation(CLILongName.class);
        CLIShortName cliShortName = field.getAnnotation(CLIShortName.class);
        Format format = getFormat(field.getAnnotation(CLIFormat.class), field.getAnnotation(CLIMessageFormat.class));
        CLIArgName argName = field.getAnnotation(CLIArgName.class);

        if (description == null && cliGroup == null && cliLongName == null && cliShortName == null && format == null)
          continue;

        if (Modifier.isFinal(modifiers))
        {
          if (!Setable.class.isAssignableFrom(field.getType()))
            throw new ParserException("CLI annotations on final fields");
        }
        else
        {
          Group group = getGroup(cliGroup, clazzGroup);
          testAndAdd(args, new ArgField<>(clazz, field, cliLongName, cliShortName, argName, group, description, format));
          continue;
        }
        Class<?> writableType = null;
        for (Type iface : field.getType().getGenericInterfaces())
        {
          if (!iface.getTypeName().startsWith(Setable.class.getName()))
            continue;
          ParameterizedType p = ParameterizedType.class.cast(iface);
          Type typeArguments[] = p.getActualTypeArguments();
          if (typeArguments.length != 1)
            throw new ParserException("Missing CLIWritable type information");
          writableType = Class.class.cast(typeArguments[0]);
          break;
        }
        if (writableType == null)
          throw new ParserException("Missing CLIWritable type information");
        Group group = getGroup(cliGroup, clazzGroup);
        testAndAdd(args, new ArgWritable<>(clazz, field, writableType, cliLongName, cliShortName, argName, group, description, format));
      }

      for (Method method : clazz.getMethods())
      {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || method.getAnnotations().length == 0) continue;

        CLIDescription description = method.getAnnotation(CLIDescription.class);
        CLIGroup cliGroup = method.getAnnotation(CLIGroup.class);
        CLILongName cliLongName = method.getAnnotation(CLILongName.class);
        CLIShortName cliShortName = method.getAnnotation(CLIShortName.class);
        Format format = getFormat(method.getAnnotation(CLIFormat.class), method.getAnnotation(CLIMessageFormat.class));
        CLIArgName argName = method.getAnnotation(CLIArgName.class);

        if (description == null && cliGroup == null && cliLongName == null && cliShortName == null && format == null)
          continue;

        Group group = getGroup(method.getAnnotation(CLIGroup.class), clazzGroup);
        testAndAdd(args, new ArgMethod<>(clazz, method, cliLongName, cliShortName, argName, group, description, format));
      }

      if (header != null || footer != null || !args.isEmpty())
      {
        _registered.add(clazz);

        if (header != null) clazzGroup._headers.add(header);
        if (footer != null) clazzGroup._footers.add(footer);

        for (Arg<?> arg : args)
        {
          arg.group._options.add(arg);
          options.add(arg);
        }
      }

      // If it is a module, then we should examine all the dependencies, too.
      if (recurseFilter != null)
        pending.addAll(recurseFilter.call(clazz));
    }

    return initialClazz;
  }

  void testAndAdd(List<Arg<?>> args, Arg<?> arg)
  {
    for (Arg<?> test : args)
    {
      testDuplicate(arg, test);
    }
    for (Arg<?> test : options)
    {
      testDuplicate(arg, test);
    }
    args.add(arg);
  }

  void testDuplicate(Arg<?> a, Arg<?> b)
  {
    if (a.shortName != null && b.shortName != null && a.shortName.equals(b.shortName))
      throw new ParserException("Duplicate CLI shortname declared");
    if (a.longName != null && b.longName != null && a.longName.equals(b.longName))
      throw new ParserException("Duplicate CLI longname declared");
  }

  static class Group
  {
    private LinkedList<CLIHeader> _headers = new LinkedList<>();
    private LinkedList<CLIFooter> _footers = new LinkedList<>();
    private LinkedList<Arg<?>> _options = new LinkedList<>();
  }

  static abstract class Apply<T>
  {
    private final Class<T> clazz;

    protected Apply(Class<T> clazz)
    {
      this.clazz = clazz;
    }

    public abstract void doApply(T object)
        throws IllegalAccessException, InvocationTargetException;
  }

  abstract static class Arg<T>
  {
    final Class<T> clazz;
    final String longName;
    final Character shortName;
    final Group group;
    final CLIDescription desc;
    final Format format;
    final String argName;

    private Arg(Class<T> clazz, String propertyName, CLILongName cliLongName, CLIShortName cliShortName, String argName, Group group, CLIDescription desc, Format format)
    {
      String longName;
      Character shortName;
      if (cliLongName == null && cliShortName == null)
      {
        longName = propertyName;
        if (longName.length() == 1)
        {
          shortName = longName.charAt(0);
          longName = null;
        }
        else
          shortName = null;
      }
      else
      {
        longName = cliLongName != null ? cliLongName.value() : null;
        shortName = cliShortName != null ? cliShortName.value() : null;
      }

      this.clazz = clazz;
      this.longName = longName;
      this.shortName = shortName;
      this.group = group;
      this.desc = desc;
      this.format = format;
      this.argName = argName;
    }

    public abstract Apply<T> parse(String arg)
        throws InvocationTargetException, IllegalAccessException, InstantiationException;

    public abstract boolean noArgument();
  }

  static class ArgWritable<T> extends ArgField<T>
  {
    final Class<?> writable;

    private ArgWritable(Class<T> clazz, Field field, Class<?> writable,
                        CLILongName longName, CLIShortName shortName, CLIArgName argName,
                        Group group, CLIDescription desc, Format format)
    {
      super(clazz, field, longName, shortName, argName, group, desc, format);
      this.writable = writable;
    }

    @Override
    public Apply<T> parse(final String arg)
        throws InvocationTargetException, IllegalAccessException, InstantiationException
    {
      if (format != null)
      {
        return new Apply<T>(clazz)
        {
          final Object[] values = Utils.parseString(format, arg);

          @Override
          @SuppressWarnings("unchecked")
          public void doApply(T object)
              throws IllegalAccessException, InvocationTargetException
          {
            Setable.class.cast(field.get(object)).setValue(values[0]);
          }
        };
      }
      else
      {
        return new Apply<T>(clazz)
        {
          final Object values = Utils.parseString(writable, arg);

          @Override
          @SuppressWarnings("unchecked")
          public void doApply(T object)
              throws IllegalAccessException
          {
            Setable.class.cast(field.get(object)).setValue(values);
          }
        };
      }
    }
  }

  static class ArgField<T> extends Arg<T>
  {
    final Field field;

    private ArgField(Class<T> clazz, Field field, CLILongName longName, CLIShortName shortName, CLIArgName argName, Group group, CLIDescription desc, Format format)
    {
      super(clazz, field.getName(), longName, shortName,
            argName != null ? argName.value() : field.getType().getSimpleName().toUpperCase(),
            group, desc, format);
      if (field.getType() == Void.TYPE || field.getType() == Void.class)
        throw new ParserException(String.format("Field %s#%s is void",
                                                clazz.getName(), field.getName()));
      this.field = field;
      if (noArgument() && !field.isAnnotationPresent(CLIDefault.class) &&
          field.getType() != Boolean.TYPE && field.getType() != Integer.TYPE)
        throw new ParserException(String.format("Field %s#%s is missing a CLIDefault annotation",
                                                clazz.getName(), field.getName()));
    }

    @Override
    public Apply<T> parse(String arg)
        throws InvocationTargetException, IllegalAccessException, InstantiationException
    {
      if ((arg == null || arg.isEmpty()) && field.isAnnotationPresent(CLIDefault.class))
        return parse0(field.getAnnotation(CLIDefault.class).value());
      else
        return parse0(arg);
    }

    private Apply<T> parse0(final String arg)
        throws InvocationTargetException, IllegalAccessException, InstantiationException
    {
      if (arg == null)
      {
        if (field.getType() == Boolean.TYPE || field.getType() == Boolean.class)
        {
          return new Apply<T>(clazz)
          {
            @Override
            public void doApply(T object)
                throws IllegalAccessException
            {
              field.set(object, true);
            }
          };
        }
        else
        if (field.getType() == Integer.TYPE)
        {
          return new Apply<T>(clazz)
          {
            @Override
            public void doApply(T object)
                throws IllegalAccessException
            {
              field.setInt(object, field.getInt(object) + 1);
            }
          };
        }

        throw new IllegalStateException();
      }
      else
      if (format != null)
      {
        return new Apply<T>(clazz)
        {
          final Object[] values = Utils.parseString(format, arg);

          @Override
          public void doApply(T object)
              throws IllegalAccessException, InvocationTargetException
          {
            field.set(object, values[0]);
          }
        };
      }
      else
      {
        return new Apply<T>(clazz)
        {
          final Object values = Utils.parseString(field.getType(), arg);

          @Override
          public void doApply(T object)
              throws IllegalAccessException
          {
            field.set(object, values);
          }
        };
      }
    }

    @Override
    public boolean noArgument()
    {
      return field.isAnnotationPresent(CLINoParameter.class) ||
             format == null && field.getType() == Boolean.TYPE;
    }
  }

  static class ArgMethod<T> extends Arg<T>
  {
    final Method method;

    private ArgMethod(Class<T> clazz, Method method, CLILongName longName, CLIShortName shortName, CLIArgName argName, Group group, CLIDescription desc, Format format)
    {
      super(clazz, method.getName(), longName, shortName,
            argName != null ? argName.value() : method.getParameterCount() == 1 ? method.getParameterTypes()[0].getSimpleName().toUpperCase() : "FORMAT",
            group, desc, format);
      if (format != null && method.getParameterCount() == 0)
        throw new ParserException("CLIFormat not permitted on a method with no parameters");
      if (method.getParameterCount() > 1 && format == null)
        throw new ParserException("CLIFormat required for methods with more than one parameters");
      if (method.getReturnType() != null && method.getReturnType() != Void.TYPE)
        throw new ParserException("Non-void return type not permitted on a method: ");
      this.method = method;
      if (noArgument() && method.getParameterCount() != 0 &&
          !method.isAnnotationPresent(CLIDefault.class) &&
          !(method.getParameterCount() == 1 && method.getParameterTypes()[0] == Void.class))
        throw new ParserException(String.format("Method %s#%s is missing a CLIDefault annotation",
                                                clazz.getName(), method.getName()));
    }

    @Override
    public Apply<T> parse(String arg)
        throws InvocationTargetException, IllegalAccessException, InstantiationException
    {
      if ((arg == null || arg.isEmpty()) && method.isAnnotationPresent(CLIDefault.class))
        return parse0(method.getAnnotation(CLIDefault.class).value());
      else
        return parse0(arg);
    }

    private Apply<T> parse0(final String arg)
        throws InvocationTargetException, IllegalAccessException, InstantiationException
    {
      if (method.getParameterCount() == 0)
      {
        return new Apply<T>(clazz)
        {
          @Override
          public void doApply(T object)
              throws InvocationTargetException, IllegalAccessException
          {
            method.invoke(object);
          }
        };
      }

      if (arg == null)
      {
        return new Apply<T>(clazz)
        {
          @Override
          public void doApply(T object)
              throws InvocationTargetException, IllegalAccessException
          {
            method.invoke(object, new Object[method.getParameterCount()]);
          }
        };
      }

      if (format != null)
      {
        return new Apply<T>(clazz)
        {
          final Object[] values = Utils.parseString(format, arg);

          @Override
          public void doApply(T object)
              throws InvocationTargetException, IllegalAccessException
          {
            method.invoke(object, values);
          }
        };
      }
      else
      {
        return new Apply<T>(clazz)
        {
          final Object values = Utils.parseString(method.getParameterTypes()[0], arg);

          @Override
          public void doApply(T object)
              throws InvocationTargetException, IllegalAccessException
          {
            method.invoke(object, values);
          }
        };
      }
    }

    @Override
    public boolean noArgument()
    {
      return method.isAnnotationPresent(CLINoParameter.class) ||
             method.getParameterCount() == 0 ||
             method.getParameterCount() == 1 && method.getParameterTypes()[0] == Void.class;
    }
  }

  @SuppressWarnings("unchecked")
  protected <T> void apply(T object)
      throws InvocationTargetException, IllegalAccessException
  {
    for (Apply apply : new ArrayList<>(args))
    {
      if (apply.clazz.isAssignableFrom(object.getClass()))
      {
        ((Apply<T>) apply).doApply(object);
      }
    }
  }

  /**
   * Apply parsed command line arguments to the provided instance.
   * @param object Instance to be configured.
   * @param <T> Type of instance.
   * @return configured instance.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T configure(T object)
  {
    if (object == null)
      return null;
    try
    {
      Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      LinkedList<Object> pending = new LinkedList<>();
      pending.add(object);

      Object o;
      while ((o = pending.poll()) != null)
      {
        if (seen.contains(o))
          continue;
        seen.add(o);

        apply(o);

        for (Field f : o.getClass().getFields())
        {
          int modifiers = f.getModifiers();
          if (!Modifier.isPublic(modifiers) || f.getAnnotation(CLIConfigure.class) == null)
            continue;
          pending.add(f.get(o));
        }
      }

      return object;
    }
    catch (InvocationTargetException e)
    {
      throw new ParserException(e.getCause());
    }
    catch (IllegalAccessException e)
    {
      throw new ParserException(e);
    }
  }

  /**
   * Parse the provided command line arguments.
   * @param args Arguments to be parsed.
   * @return unparsed arguments.
   */
  public String[] parse(String args[])
  {
    final ArrayList<String> list = new ArrayList<>(args.length);
    parse(args, list::add);
    return list.toArray(new String[list.size()]);
  }

  /**
   * Parse the provided command line arguments.
   * @param args Arguments to be parsed.
   * @param p callback to handle unparsed arguments.
   */
  public void parse(String args[], Callback<String> p)
  {
    try
    {
      parse0(args, p);
    }
    catch (IllegalAccessException e)
    {
      throw new ParserException(e);
    }
    catch (InstantiationException | InvocationTargetException e)
    {
      throw new ParserException(e.getCause());
    }
  }

  void parse0(String args[], Callback<String> p)
      throws IllegalAccessException, InstantiationException, InvocationTargetException
  {
    LinkedList<Arg<?>> argQueue = new LinkedList<Arg<?>>();
    boolean endOfArgs = false;
    for (String arg : args)
    {
      if (!endOfArgs)
      {
        if ((endOfArgs = "--".equalsIgnoreCase(arg))) continue;

        if (arg.startsWith("-"))
        {
          Arg<?> found;
          if (arg.startsWith("--"))
          {
            String name, opt;
            int eqPos = arg.indexOf('=');
            if (eqPos > 0)
            {
              opt = arg.substring(eqPos + 1);
              name = arg.substring(2, eqPos);
            }
            else
            {
              opt = null;
              name = arg.substring(2);
            }

            found = null;
            for (Arg<?> option : options)
            {
              if (!name.equals(option.longName)) continue;
              found = option;
              break;
            }
            if (found == null) throw new BadArgumentException("Unknown option: " + name);

            if (opt != null) this.args.add(found.parse(opt));
            else argQueue.add(found);
          }
          else
          {
            for (int i = 1; i < arg.length(); i++)
            {
              Character ch = arg.charAt(i);
              found = null;
              for (Arg<?> option : options)
              {
                if (!ch.equals(option.shortName)) continue;
                found = option;
                break;
              }
              if (found == null) throw new BadArgumentException("Unknown option: " + ch);

              argQueue.add(found);
            }
          }
          continue;
        }
      }

      Arg<?> expect = consumeNoArgument(argQueue);
      if (expect == null)
      {
        p.call(arg);
      }
      else
      {
        this.args.add(expect.parse(arg));
      }
    }

    Arg<?> expect = consumeNoArgument(argQueue);
    if (expect != null)
      throw new BadArgumentException("Missing arguments; expected argument for " + expect.argName);
  }

  Arg<?> consumeNoArgument(LinkedList<Arg<?>> argQueue)
      throws IllegalAccessException, InstantiationException, InvocationTargetException
  {
    Arg<?> expect;
    while ((expect = argQueue.pollFirst()) != null && expect.noArgument())
    {
      this.args.add(expect.parse(null));
    }
    return expect;
  }
}

package org.hl7.fhir.r5.renderers;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities.SourcedChildDefinitions;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper.NamedResourceWrapperList;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.r5.utils.XVerExtensionManager;
import org.hl7.fhir.r5.utils.XVerExtensionManager.XVerExtensionStatus;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class ProfileDrivenRenderer extends ResourceRenderer {

  private Set<String> containedIds = new HashSet<>();
  
  public ProfileDrivenRenderer(RenderingContext context) {
    super(context);
  }

  @Override
  public void buildNarrative(RenderingStatus status, XhtmlNode x, ResourceWrapper r) throws FHIRFormatError, DefinitionException, IOException {
    renderResourceTechDetails(r, x);
    try {
      StructureDefinition sd = context.getContext().fetchTypeDefinition(r.fhirType());
      if (sd == null) {
        throw new FHIRException(context.formatPhrase(RenderingContext.PROF_DRIV_FEXCP, r.fhirType())+" ");
      } else {
        ElementDefinition ed = sd.getSnapshot().getElement().get(0);
        containedIds.clear();
        generateByProfile(status, r, sd, r, sd.getSnapshot().getElement(), ed, context.getProfileUtilities().getChildList(sd, ed), x, r.fhirType(), context.isTechnicalMode(), 0);
      }
    } catch (Exception e) {
      System.out.println(context.formatPhrase(RenderingContext.PROF_DRIV_ERR_GEN_NARR) +r.fhirType()+"/"+r.getId()+": "+e.getMessage());
      e.printStackTrace();
      x.para().b().style("color: maroon").tx(context.formatPhrase(RenderingContext.PROF_DRIV_EXCP, e.getMessage())+" ");
    }
  }

  
  @Override
  public String buildSummary(ResourceWrapper res) throws UnsupportedEncodingException, IOException {
    StructureDefinition profile = getContext().getWorker().fetchTypeDefinition(res.fhirType());
    if (profile == null)
      return "unknown resource type " +res.fhirType();
    else {
      boolean firstElement = true;
      boolean last = false;
      List<ResourceWrapper> children = res.children();
      ContextUtilities cu = res.getContextUtilities();
      for (ResourceWrapper p : children) {
        if (p.name().equals("title") && cu.isDatatype(p.fhirType()) && !p.isEmpty()) {
          return res.fhirType()+" "+ displayDataType(p);
        }
      }
      for (ResourceWrapper p : children) {
        if (p.name().equals("name") && cu.isDatatype(p.fhirType()) && !p.isEmpty()) {
          return res.fhirType()+" "+ displayDataType(p);
        }
      }
      for (ResourceWrapper p : children) {
        if (p.name().equals("code") && cu.isDatatype(p.fhirType()) && !p.isEmpty()) {
          return res.fhirType()+" "+ displayDataType(p);
        }
      }
      switch (res.fhirType()) {
      case "Binary" : return res.fhirType()+": "+ res.primitiveValue("data").length()+" chars (base64)";
      }
      return generateResourceSummary(res, profile, profile.getSnapshot().getElementFirstRep(), false, false);
    }
  }

  public String generateResourceSummary(ResourceWrapper res, StructureDefinition sd, ElementDefinition ed, boolean showCodeDetails, boolean canLink) throws FHIRException, UnsupportedEncodingException, IOException {
    if (sd == null)
      return "unknown resource " +res.fhirType();
    else {
      SourcedChildDefinitions childDefs = context.getProfileUtilities().getChildMap(sd, ed);
      CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder("; ");
      for (NamedResourceWrapperList p : res.childrenInGroups()) {
        ElementDefinition pDefn = getElementDefinition(childDefs, p); 
        if (pDefn != null && !ignoreProperty(p) && !pDefn.getBase().getPath().startsWith("Resource.")) {
          if (p.getValues().size() > 0 && p.getValues().get(0) != null && pDefn != null && isSimple(pDefn) && includeInSummary(pDefn, p)) {
            CommaSeparatedStringBuilder b2 = new CommaSeparatedStringBuilder(",");
            for (ResourceWrapper v : p.getValues()) {
              b2.append(displayDataType(v));
            }
            b.append(formatPhrase(RenderingContext.PROF_DRIV_SUMM_PROP, labelForElement(pDefn), b2.toString()));
          }
        }
      }
      if (b.length() == 0) {
        return formatPhrase(RenderingContext.PROF_DRIV_SUMM_NONE, res.fhirType());        
      } else {
        return formatPhrase(RenderingContext.PROF_DRIV_SUMM, res.fhirType(), b.toString());
      }
    }
  }
  
  private String labelForElement(ElementDefinition pDefn) {
    return pDefn.getName();
  }

  private ElementDefinition getElementDefinition(SourcedChildDefinitions childDefs, NamedResourceWrapperList p) {
    for (ElementDefinition ed : childDefs.getList()) {
      if (ed.getName().equals(p.getName())) {
        return ed;
      }
    }
    return null;
  }

  private boolean ignoreProperty(NamedResourceWrapperList p) {
    return Utilities.existsInList(p.getName(), "contained");
  }

  private boolean includeInSummary(ElementDefinition child, NamedResourceWrapperList list) throws UnsupportedEncodingException, FHIRException, IOException {
    if (child.getName().endsWith("active") && list != null && list.getValues().size() > 0 && "true".equals(list.getValues().get(0).primitiveValue())) {
      return false;
    }
    if (child.getIsModifier())
      return true;
    if (child.getMustSupport())
      return true;
    if (child.getType().size() == 1) {
      String t = child.getType().get(0).getWorkingCode();
      if (t.equals("Address") || t.equals("Contact") || t.equals("Reference") || t.equals("Uri") || t.equals("Url") || t.equals("Canonical"))
        return false;
    }
    return true;
  }
  
  private ElementDefinition getElementDefinition(List<ElementDefinition> elements, String path) {
    for (ElementDefinition element : elements)
      if (element.getPath().equals(path))
        return element;
    return null;
  }

  private void renderLeaf(RenderingStatus status, ResourceWrapper res, ResourceWrapper ew, ElementDefinition defn, XhtmlNode parent, XhtmlNode x, boolean title, boolean showCodeDetails, Map<String, String> displayHints, String path, int indent) throws FHIRException, UnsupportedEncodingException, IOException, EOperationOutcome {
    if (ew == null)
      return;

    if (context.isShowComments()) {
      x = renderCommentsSpan(x, ew);
    }
    if (Utilities.existsInList(ew.fhirType(), "Extension") || ew.isResource()) {
      return;
    } else if (ew.fhirType().equals("ElementDefinition")) {
      x.tx("todo-bundle");
    } else if (!renderDataType(status, parent, x, ew)) {
      if (Utilities.existsInList(ew.fhirType(), "Attachment", "Narrative", "Meta", "ProductShelfLife", "RelatedArtifact")) {
        throw new NotImplementedException("type "+ew.fhirType()+" not handled. This may be due to unresolved inter-version compatibility issues");
      }
    }    
  }

  private XhtmlNode renderCommentsSpan(XhtmlNode x, ResourceWrapper e) {
    if (e.hasFormatComment()) {      
      return x.span(null, CommaSeparatedStringBuilder.join("&#10;", e.getFormatCommentsPre()));
    } else {
      return x;
    }
  }
  
  private boolean displayLeaf(ResourceWrapper res, ResourceWrapper ew, ElementDefinition defn, XhtmlNode x, String name, boolean showCodeDetails, boolean allowLinks) throws FHIRException, UnsupportedEncodingException, IOException {
    if (ew == null)
      return false;

    Map<String, String> displayHints = readDisplayHints(defn);

    if (name.endsWith("[x]"))
      name = name.substring(0, name.length() - 3);

    if (!showCodeDetails && ew.isPrimitive() && isDefault(displayHints, ew)) {
      return false;
    } else if (Utilities.existsInList(ew.fhirType(), "Extension")) {
      return false;
    } else {
      x.addText(name+": "+ displayDataType(ew));
      return true;
    }
  }



  private boolean isSimple(ElementDefinition e) {
    //we can tell if e is a primitive because it has types
    if (e.getType().isEmpty()) {
      return false;
    }
    if (e.getType().size() == 1 && isBase(e.getType().get(0).getWorkingCode())) {
      return false;
    }
    if (e.getType().size() > 1) {
      return true;
    }
    StructureDefinition sd = context.getWorker().fetchTypeDefinition(e.getTypeFirstRep().getCode());
    if (sd != null) {
      if (sd.getKind() == StructureDefinitionKind.PRIMITIVETYPE) {
        return true;
      }
      if (sd.getKind() == StructureDefinitionKind.COMPLEXTYPE) {
        if (Utilities.existsInList(e.getTypeFirstRep().getCode(), "Extension", "CodeableConcept", "Coding", "Annotation", "Identifier", "HumanName", "SampledData", 
            "Address", "ContactPoint", "ContactDetail", "Timing", "Range", "Quantity", "Ratio", "Period", "Reference")) {
          return true;
        }        
      }
    }
    return false;
  }

  private boolean isBase(String code) {
    return code != null && (code.equals("Element") || code.equals("BackboneElement"));
  }
  
  private List<ElementDefinition> getChildrenForPath(StructureDefinition profile, List<ElementDefinition> elements, String path) throws DefinitionException {
    // do we need to do a name reference substitution?
    for (ElementDefinition e : elements) {
      if (e.getPath().equals(path) && e.hasContentReference()) {
        String ref = e.getContentReference();
        ElementDefinition t = null;
        // now, resolve the name
        for (ElementDefinition e1 : elements) {
          if (ref.equals("#"+e1.getId()))
            t = e1;
        }
        if (t == null)
          throw new DefinitionException("Unable to resolve content reference "+ref+" trying to resolve "+path);
        path = t.getPath();
        break;
      }
    }

    ElementDefinition t = null;
    List<ElementDefinition> results = new ArrayList<ElementDefinition>();
    for (ElementDefinition e : elements) {
      if (e.getPath().equals(path)) {
        t = e; 
      }
      if (e.getPath().startsWith(path+".") && !e.getPath().substring(path.length()+1).contains("."))
        results.add(e);
    }
    if (results.isEmpty() && t != null && t.getType().size() == 1) {
       StructureDefinition tsd = context.getWorker().fetchTypeDefinition(t.getTypeFirstRep().getWorkingCode());
       return getChildrenForPath(tsd, tsd.getSnapshot().getElement(), tsd.getType());
    }
    return results;
  }

  private void generateByProfile(RenderingStatus status, ResourceWrapper res, StructureDefinition profile, ResourceWrapper e, List<ElementDefinition> allElements, ElementDefinition defn, List<ElementDefinition> children,  XhtmlNode x, String path, boolean showCodeDetails, int indent) throws FHIRException, UnsupportedEncodingException, IOException, EOperationOutcome {
    if (children.isEmpty()) {
      StructureDefinition sdt = context.getWorker().fetchTypeDefinition(e.fhirType());
      if (sdt != null && (sdt.getKind() == StructureDefinitionKind.COMPLEXTYPE || sdt.getKind() == StructureDefinitionKind.PRIMITIVETYPE)) {
        renderLeaf(status, res, e, defn, x, x, false, showCodeDetails, readDisplayHints(defn), path, indent);
      } else {
        // we don't have anything to render?
      }
    } else {
      List<NamedResourceWrapperList> pl = splitExtensions(profile, e.childrenInGroups());
      for (NamedResourceWrapperList p : pl) {
        generateForProperty(status, res, profile, allElements, children, x, path, showCodeDetails, indent, false, p);
      }
      for (NamedResourceWrapperList p : pl) {
        generateForProperty(status, res, profile, allElements, children, x, path, showCodeDetails, indent, true, p);
      }
    }
  }

  private void generateForProperty(RenderingStatus status, ResourceWrapper res, StructureDefinition profile,
      List<ElementDefinition> allElements, List<ElementDefinition> children, XhtmlNode x, String path,
      boolean showCodeDetails, int indent, boolean round2, NamedResourceWrapperList p)
      throws UnsupportedEncodingException, IOException, EOperationOutcome {
    if (!p.getValues().isEmpty()) {
      ElementDefinition child = getElementDefinition(children, path+"."+p.getName());
      if (child != null) {
        if (!child.getBase().hasPath() || !child.getBase().getPath().startsWith("Resource.")) {
          generateElementByProfile(status, res, profile, allElements, x, path, showCodeDetails, indent, p, child, round2);
        }
      }
    }
  }

  public void generateElementByProfile(RenderingStatus status, ResourceWrapper res, StructureDefinition profile, List<ElementDefinition> allElements, XhtmlNode x, String path,
      boolean showCodeDetails, int indent, NamedResourceWrapperList p, ElementDefinition child, boolean round2) throws UnsupportedEncodingException, IOException, EOperationOutcome {
    Map<String, String> displayHints = readDisplayHints(child);
    if ("DomainResource.contained".equals(child.getBase().getPath())) {
      if (round2) {
        for (ResourceWrapper v : p.getValues()) {
          RenderingContext ctxt = context.forContained();
          if (v.getResourceWrapper() != null && !RendererFactory.hasSpecificRenderer(v.fhirType())) {
            x.hr();
            ResourceRenderer rnd = RendererFactory.factory(v.fhirType(), ctxt);
            rnd.buildNarrative(status, x.blockquote(), v);
          }
        }
      }
    } else if (!round2 && !exemptFromRendering(child)) {
      if (isExtension(p)) {
        status.setExtensions(true);
      }
      List<ElementDefinition> grandChildren = getChildrenForPath(profile, allElements, path+"."+p.getName());
      filterGrandChildren(grandChildren, path+"."+p.getName(), p);
      if (p.getValues().size() > 0) {
         if (isSimple(child)) {
           XhtmlNode para = x.isPara() ? para = x : x.para();
           String name = p.getName();
           if (name.endsWith("[x]"))
             name = name.substring(0, name.length() - 3);
           if (showCodeDetails || !isDefaultValue(displayHints, p.getValues())) {

             para.b().addText(name);
             para.tx(": ");
             if (renderAsList(child) && p.getValues().size() > 1) {
               XhtmlNode list = x.ul();
               for (ResourceWrapper v : p.getValues())
                 renderLeaf(status, res, v, child, x, list.li(), false, showCodeDetails, displayHints, path, indent);
             } else {
               boolean first = true;
               for (ResourceWrapper v : p.getValues()) {
                 if (first) {
                   first = false;
                 } else {
                   para.tx(", ");
                 }
                 renderLeaf(status, res, v, child, x, para, false, showCodeDetails, displayHints, path, indent);
               }
             }
           }
        } else if (canDoTable(path, p, grandChildren, x)) {
          XhtmlNode xn = new XhtmlNode(NodeType.Element, getHeader());
          xn.addText(Utilities.capitalize(Utilities.camelCase(Utilities.pluralizeMe(p.getName()))));
          XhtmlNode tbl = new XhtmlNode(NodeType.Element, "table"); 
          tbl.setAttribute("class", "grid");
          XhtmlNode tr = tbl.tr();
          tr.td().style("display: none").tx("-"); // work around problem with empty table rows
          boolean add = addColumnHeadings(tr, grandChildren);          
          for (ResourceWrapper v : p.getValues()) {
            if (v != null) {
              tr = tbl.tr();
              tr.td().style("display: none").tx("*"); // work around problem with empty table rows
              add = addColumnValues(status, res, tr, grandChildren, v, showCodeDetails, displayHints, path, indent) || add;
            }
          }
          if (add) {
            x.add(xn);
            x.add(tbl);
          }
        } else if (isExtension(p)) {
          for (ResourceWrapper v : p.getValues()) {
            if (v != null) {
              ResourceWrapper vp = v.child("value");
              List<ResourceWrapper> ev = v.children("extension");
              if (vp != null) {
                XhtmlNode para = x.para();
                para.b().addText(labelforExtension(p.getName()));
                para.tx(": ");
                renderLeaf(status, res, vp, child, x, para, false, showCodeDetails, displayHints, path, indent);
              } else if (!ev.isEmpty()) {
                XhtmlNode bq = x.addTag("blockquote");                
                bq.para().b().addText(labelforExtension(p.getName()));
                for (ResourceWrapper vv : ev) {
                  StructureDefinition ex = context.getWorker().fetchTypeDefinition("Extension");
                  List<ElementDefinition> children = getChildrenForPath(profile, ex.getSnapshot().getElement(), "Extension");
                  generateByProfile(status, res, ex, vv, allElements, child, children, bq, "Extension", showCodeDetails, indent+1);
                }
              }
            }
          }          
        } else {
          for (ResourceWrapper v : p.getValues()) {
            if (v != null) {
              XhtmlNode bq = x.addTag("blockquote");
              bq.para().b().addText(p.getName());
              generateByProfile(status, res, profile, v, allElements, child, grandChildren, bq, path+"."+p.getName(), showCodeDetails, indent+1);
            }
          }
        }
      }
    }
  }


  private String labelforExtension(String url) {
    StructureDefinition sd = context.getContext().fetchResource(StructureDefinition.class, url);
    if (sd == null) {
      return tail(url);
    } else {
      return sd.present();
    }
  }

  private String getHeader() {
    int i = 3;
    while (i <= context.getHeaderLevelContext())
      i++;
    if (i > 6)
      i = 6;
    return "h"+Integer.toString(i);
  }

  private List<ResourceWrapper> getValues(String path, NamedResourceWrapperList p, ElementDefinition e) {
    List<ResourceWrapper> res = new ArrayList<ResourceWrapper>();
    for (ResourceWrapper v : p.getValues()) {
      for (ResourceWrapper g : v.children()) {
        if ((path+"."+p.getName()+"."+g.name()).equals(e.getPath()))
          res.add(v);
      }
    }
    return res;
  }
  
  private boolean canDoTable(String path, NamedResourceWrapperList p, List<ElementDefinition> grandChildren, XhtmlNode x) {
    if (isExtension(p)) {
      return false;
    }
    if (x.getName().equals("p")) {
      return false;
    }
    
    if (grandChildren.size() == 0) {
      return false;
    }

    for (ElementDefinition e : grandChildren) {
      List<ResourceWrapper> values = getValues(path, p, e);
      if (values.size() > 1 || !isSimple(e) || !canCollapse(e))
        return false;
    }
    return true;
  }

  public boolean isExtension(NamedResourceWrapperList p) {
    return p.getName().contains("extension[");
  }


  private boolean canCollapse(ElementDefinition e) {
    // we can collapse any data type
    return !e.getType().isEmpty();
  }
  private boolean exemptFromRendering(ElementDefinition child) {
    if (child == null)
      return false;
    if ("DomainResource.text".equals(child.getBase().getPath())) {
      return true;
    }
    if ("Composition.subject".equals(child.getPath())) {
      return true;
    }
    if ("Composition.section".equals(child.getPath())) {
      return true;
    }
    return false;
  }

  private boolean renderAsList(ElementDefinition child) {
    if (child.getType().size() == 1) {
      String t = child.getType().get(0).getWorkingCode();
      if (t.equals("Address") || t.equals("Reference"))
        return true;
    }
    return false;
  }

  private boolean addColumnHeadings(XhtmlNode tr, List<ElementDefinition> grandChildren) {
    boolean b = false;
    for (ElementDefinition e : grandChildren) {
      b = true;
      tr.td().b().addText(Utilities.capitalize(tail(e.getPath())));
    }
    return b;
  }

  private boolean addColumnValues(RenderingStatus status, ResourceWrapper res, XhtmlNode tr, List<ElementDefinition> grandChildren, ResourceWrapper v, boolean showCodeDetails, Map<String, String> displayHints, String path, int indent) throws FHIRException, UnsupportedEncodingException, IOException, EOperationOutcome {
    boolean b = false;
    for (ElementDefinition e : grandChildren) {
      List<ResourceWrapper> p = v.children(e.getPath().substring(e.getPath().lastIndexOf(".")+1));
      XhtmlNode td = tr.td();
      if (p == null || p.size() == 0) {
        b = true;
        td.tx(" ");
      } else {
        for (ResourceWrapper vv : p) {
          b = true;
          td.sep(", ");
          renderLeaf(status, res, vv, e, td, td, false, showCodeDetails, displayHints, path, indent);
        }
      }
    }
    return b;
  }

  private void filterGrandChildren(List<ElementDefinition> grandChildren,  String string, NamedResourceWrapperList prop) {
    List<ElementDefinition> toRemove = new ArrayList<ElementDefinition>();
    toRemove.addAll(grandChildren);
    for (ResourceWrapper b : prop.getValues()) {
      List<ElementDefinition> list = new ArrayList<ElementDefinition>();
      for (ElementDefinition ed : toRemove) {
        List<ResourceWrapper> p = b.children(tail(ed.getPath()));
        if (p != null && !p.isEmpty())
          list.add(ed);
      }
      toRemove.removeAll(list);
    }
    grandChildren.removeAll(toRemove);
  }

  private List<NamedResourceWrapperList> splitExtensions(StructureDefinition profile, List<NamedResourceWrapperList> children) throws UnsupportedEncodingException, IOException, FHIRException {
    List<NamedResourceWrapperList> results = new ArrayList<NamedResourceWrapperList>();
    for (NamedResourceWrapperList p : children) {
      if (p.getName().equals("extension") || p.getName().equals("modifierExtension")) {
        // we're going to split these up, and create a property for each url
        for (ResourceWrapper v : p.getValues()) {
          String url = v.primitiveValue("url");
          if (url != null) {
            // 1. check extension is valid
            StructureDefinition ed = getContext().getWorker().fetchResource(StructureDefinition.class, url);
            if (ed == null) {
              if (xverManager == null) {
                xverManager = new XVerExtensionManager(context.getWorker());
              }
              if (xverManager.matchingUrl(url) && xverManager.status(url) == XVerExtensionStatus.Valid) {
                ed = xverManager.makeDefinition(url);
                new ContextUtilities(getContext().getWorker()).generateSnapshot(ed);
                getContext().getWorker().cacheResource(ed);
              } 
            }
            if (p.getName().equals("modifierExtension") && ed == null) {
              throw new DefinitionException("Unknown modifier extension "+url);
            } else {
              // nothing
            }

            // 2. Park it
            NamedResourceWrapperList nl = null;
            for (NamedResourceWrapperList t : results) {
              if (t.getName().equals(url)) {
                nl = t;
              }
            }
            if (nl == null) {
              nl = new NamedResourceWrapperList(url);
              results.add(nl);
            }
            nl.getValues().add(v);
          }
        }          
      } else {
        results.add(p);
      }
    }
    return results;
  }


  private Map<String, String> readDisplayHints(ElementDefinition defn) throws DefinitionException {
    Map<String, String> hints = new HashMap<String, String>();
    if (defn != null) {
      String displayHint = ToolingExtensions.getDisplayHint(defn);
      if (!Utilities.noString(displayHint)) {
        String[] list = displayHint.split(";");
        for (String item : list) {
          String[] parts = item.split(":");
          if (parts.length == 1) {
            hints.put("value", parts[0].trim());            
          } else {
            if (parts.length != 2) {
              throw new DefinitionException("error reading display hint: '"+displayHint+"'");
            }
            hints.put(parts[0].trim(), parts[1].trim());
          }
        }
      }
    }
    return hints;
  }

  @SuppressWarnings("rawtypes")
  private boolean isDefaultValue(Map<String, String> displayHints, List<ResourceWrapper> list) throws UnsupportedEncodingException, IOException, FHIRException {
    if (list.size() != 1)
      return false;
    if (list.get(0).isPrimitive())
      return isDefault(displayHints, list.get(0));
    else
      return false;
  }

  private boolean isDefault(Map<String, String> displayHints, ResourceWrapper primitiveType) {
    String v = primitiveType.primitiveValue();
    if (!Utilities.noString(v) && displayHints.containsKey("default") && v.equals(displayHints.get("default")))
      return true;
    return false;
  }


  protected String tail(String path) {
    return path.substring(path.lastIndexOf(".")+1);
  }

  public boolean canRender(Resource resource) {
    return context.getWorker().getResourceNames().contains(resource.fhirType());
  }

  public RendererType getRendererType() {
    return RendererType.PROFILE;
  }

}
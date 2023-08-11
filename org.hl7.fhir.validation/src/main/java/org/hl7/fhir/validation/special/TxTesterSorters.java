package org.hl7.fhir.validation.special;

import java.util.Collections;
import java.util.Comparator;

import org.hl7.fhir.ParametersParameter;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptPropertyComponent;
import org.hl7.fhir.r5.model.ValueSet.ConceptReferenceDesignationComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionParameterComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionPropertyComponent;

public class TxTesterSorters {



  public static void sortParameters(Parameters po) {
    Collections.sort(po.getParameter(), new TxTesterSorters.ParameterSorter());
    for (ParametersParameterComponent p : po.getParameter()) {
      if (p.getResource() != null && p.getResource() instanceof OperationOutcome) {
        Collections.sort(((OperationOutcome) p.getResource()).getIssue(), new TxTesterSorters.OperationIssueSorter());
      }
    }
  }
  

  public static void sortValueSet(ValueSet vs) {
    Collections.sort(vs.getExtension(), new TxTesterSorters.ExtensionSorter());
    if (vs.hasExpansion()) {
      Collections.sort(vs.getExpansion().getParameter(), new TxTesterSorters.ExpParameterSorter());
      Collections.sort(vs.getExpansion().getProperty(), new TxTesterSorters.PropertyDefnSorter());
      Collections.sort(vs.getExpansion().getExtension(), new TxTesterSorters.ExtensionSorter());
      for (ValueSetExpansionContainsComponent cc : vs.getExpansion().getContains()) {
        sortContainsFeatures(cc);
      }
    }
  }

  public static void sortContainsFeatures(ValueSetExpansionContainsComponent cc) {
    Collections.sort(cc.getExtension(), new TxTesterSorters.ExtensionSorter());
    Collections.sort(cc.getDesignation(), new TxTesterSorters.DesignationSorter());
    Collections.sort(cc.getProperty(), new TxTesterSorters.PropertyValueSorter());
    for (ValueSetExpansionContainsComponent c : cc.getContains()) {
      sortContainsFeatures(c);
    }
  }

  

  public static class OperationIssueSorter implements Comparator<OperationOutcomeIssueComponent> {

    @Override
    public int compare(OperationOutcomeIssueComponent o1, OperationOutcomeIssueComponent o2) {
      if (o1.hasSeverity() && o2.hasSeverity() && o1.getSeverity() != o2.getSeverity()) {
        return o1.getSeverity().compareTo(o2.getSeverity());
      }
      if (o1.hasCode() && o2.hasCode() && o1.getCode() != o2.getCode()) {
        return o1.getCode().compareTo(o2.getCode());
      }
      if (o1.getDetails().hasText() && o2.getDetails().hasText() && !o1.getDetails().getText().equals(o2.getDetails().getText())) {
        return o1.getDetails().getText().compareTo(o2.getDetails().getText());
      }
      return 0;
    }
  }

  public static class DesignationSorter implements Comparator<ConceptReferenceDesignationComponent> {

    @Override
    public int compare(ConceptReferenceDesignationComponent o1, ConceptReferenceDesignationComponent o2) {
      if (o1.hasLanguage() && o2.hasLanguage()) {
        return o1.getLanguage().compareTo(o2.getLanguage());
      } else {
        return o1.getValue().compareTo(o2.getValue());
      }
    }

  }

  public static class PropertyDefnSorter implements Comparator<ValueSetExpansionPropertyComponent> {

    @Override
    public int compare(ValueSetExpansionPropertyComponent o1, ValueSetExpansionPropertyComponent o2) {
      int i = o1.getUri().compareTo(o2.getUri());
      if (i == 0) {
        return o1.getCode().compareTo(o2.getCode());
      } else {
        return i;
      }
    }

  }

  public static class ExtensionSorter implements Comparator<Extension> {

    @Override
    public int compare(Extension o1, Extension o2) {
      Collections.sort(o1.getExtension(), new ExtensionSorter());
      Collections.sort(o2.getExtension(), new ExtensionSorter());
      return o1.getUrl().compareTo(o2.getUrl());
    }

  }
  public static class PropertyValueSorter implements Comparator<ConceptPropertyComponent> {

    @Override
    public int compare(ConceptPropertyComponent o1, ConceptPropertyComponent o2) {
      return o1.getCode().compareTo(o2.getCode());
    }

  }
  

  public static class ExpParameterSorter implements Comparator<ValueSetExpansionParameterComponent> {

    @Override
    public int compare(ValueSetExpansionParameterComponent o1, ValueSetExpansionParameterComponent o2) {
      Collections.sort(o1.getExtension(), new ExtensionSorter());
      Collections.sort(o2.getExtension(), new ExtensionSorter());
      int res = o1.getName().compareTo(o2.getName());
      if (res == 0) {
        res = o1.getValue().primitiveValue().compareTo(o2.getValue().primitiveValue());
      }
      return res;
    }

  }
  
  
  public static class ParameterSorter implements Comparator<ParametersParameterComponent> {

    @Override
    public int compare(ParametersParameterComponent o1, ParametersParameterComponent o2) {
      Collections.sort(o1.getExtension(), new ExtensionSorter());
      Collections.sort(o2.getExtension(), new ExtensionSorter());
      Collections.sort(o1.getPart(), new ParameterSorter());
      Collections.sort(o2.getPart(), new ParameterSorter());
      return o1.getName().compareTo(o2.getName());
    }

  }
  
  
  
}

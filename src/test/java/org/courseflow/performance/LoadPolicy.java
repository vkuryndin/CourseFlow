package org.courseflow.performance;

/** Only explicit service-unavailable responses qualify as overload; transport failures do not. */
final class LoadPolicy {
  private LoadPolicy() {}

  static boolean controlledOverload(LoadRunner.Sample sample) {
    return sample.exception().isEmpty() && sample.status() == 503;
  }
}

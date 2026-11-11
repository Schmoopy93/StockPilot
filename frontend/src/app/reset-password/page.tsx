import AuthFlow from "@/components/auth-flow";
import { queryValue } from "@/lib/routes";

export default async function ResetPasswordPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[]; token?: string | string[] }>;
}) {
  const params = await searchParams;
  return (
    <AuthFlow
      mode="reset"
      token={queryValue(params.token)}
      nextPath={queryValue(params.next)}
    />
  );
}
